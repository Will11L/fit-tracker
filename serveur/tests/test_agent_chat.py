"""Tests Cas C — Agent IA in-app (Phase 2 MCP).

Anthropic est mocké (aucun appel réseau, aucune clé requise) : on injecte un
faux client dont `messages.create` rejoue une séquence scriptée (tool_use puis
texte final). Les tools, eux, tournent en VRAI contre la DB de test
(fittracker_test) via le `patch_session` partagé — on vérifie ainsi la boucle
tool-use de bout en bout + l'isolation cross-user + le filtrage de scope.

Couvre : boucle tool-use (read + write), récap tool_calls, isolation
cross-user (le tool ne mute pas la donnée d'un autre user), allow-list de
scope (un tool destructif n'est jamais exposé ni exécutable), garde-fou
max-iterations, et le contrat HTTP (503 sans clé, 401 sans auth, rate limit).
"""
from datetime import date

import bcrypt
import pytest
import pytest_asyncio
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.agent import chat as agent_chat
from app.agent import tool_bridge
from app.mcp import audit as mcp_audit
from app.mcp.context import set_mcp_context
from app.mcp.tools import sport_data, sport_destructive, sport_dev, sport_write
from app.models import User
from app.models.actual_workout import ActualWorkout
from app.models.actual_workout_exercise import ActualWorkoutExercise
from app.models.actual_workout_set import ActualWorkoutSet
from app.models.exercise import Exercise
from app.settings import settings


# ---- Faux client Anthropic ----

class _Block:
    """Bloc de contenu Anthropic minimal (text OU tool_use)."""

    def __init__(self, type, text=None, name=None, id=None, input=None):
        self.type = type
        self.text = text
        self.name = name
        self.id = id
        self.input = input


class _Resp:
    def __init__(self, stop_reason, content):
        self.stop_reason = stop_reason
        self.content = content


class _FakeMessages:
    def __init__(self, scripted):
        self._scripted = list(scripted)
        self.calls = []

    async def create(self, **kwargs):
        self.calls.append(kwargs)
        return self._scripted.pop(0)


class _FakeClient:
    def __init__(self, scripted):
        self.messages = _FakeMessages(scripted)


def _patch_client(monkeypatch, scripted):
    """Force run_agent_chat à utiliser un client scripté. Retourne le client
    (pour inspecter `.messages.calls`)."""
    fake = _FakeClient(scripted)
    monkeypatch.setattr(agent_chat, "_build_client", lambda: fake)
    return fake


@pytest_asyncio.fixture(scope="module")
async def agent_seed(test_engine):
    """User + une séance avec un set NOT_STARTED (pour tester mark_set_done)."""
    maker = async_sessionmaker(test_engine, expire_on_commit=False)
    async with maker() as db:
        user = (
            await db.execute(select(User).where(User.username == "agent_testuser"))
        ).scalar_one_or_none()
        if user is None:
            user = User(
                username="agent_testuser",
                hashed_password=bcrypt.hashpw(b"pw", bcrypt.gensalt()).decode("utf-8"),
                first_name="Agent", last_name="Test", is_admin=False,
            )
            db.add(user)
            await db.flush()
            uid = user.id
            ex = Exercise(user_id=uid, name="Agent Squat")
            db.add(ex)
            await db.flush()
            aw = ActualWorkout(user_id=uid, name="Agent Session",
                               date=date.today().isoformat(), is_done=False)
            db.add(aw)
            await db.flush()
            awe = ActualWorkoutExercise(
                actual_workout_uuid=aw.uuid, exercise_uuid=ex.uuid,
                sets=1, reps="8", status="NOT_STARTED", order=0,
            )
            db.add(awe)
            await db.flush()
            db.add(ActualWorkoutSet(actual_workout_exercise_uuid=awe.uuid,
                                    set_order=0, reps=0, weight=0.0, status="NOT_STARTED"))
            await db.commit()
        uid = user.id
        aw = (await db.execute(
            select(ActualWorkout).where(ActualWorkout.user_id == uid)
        )).scalars().first()
        set0 = (await db.execute(
            select(ActualWorkoutSet)
            .join(ActualWorkoutExercise,
                  ActualWorkoutSet.actual_workout_exercise_uuid == ActualWorkoutExercise.uuid)
            .where(ActualWorkoutExercise.actual_workout_uuid == aw.uuid)
        )).scalars().first()
    return {"user_id": uid, "aw_uuid": aw.uuid, "set_uuid": set0.uuid}


@pytest.fixture(autouse=True)
def patch_session(monkeypatch, test_engine):
    """Redirige AsyncSessionLocal de tous les modules tools vers la DB de test
    (même garde-fou que test_mcp_tools : éviter d'écrire en prod)."""
    test_maker = async_sessionmaker(test_engine, expire_on_commit=False)
    for mod in (sport_data, sport_write, sport_destructive, sport_dev, mcp_audit):
        monkeypatch.setattr(mod, "AsyncSessionLocal", test_maker)


# ============================================================
# Boucle tool-use
# ============================================================


async def test_tool_use_loop_read(monkeypatch, agent_seed):
    """1er tour: le modèle appelle list_muscles ; 2e tour: texte final."""
    set_mcp_context(user_id=agent_seed["user_id"], client_id="in-app-agent",
                    scopes=["sport:read", "sport:write"])
    fake = _patch_client(monkeypatch, [
        _Resp("tool_use", [_Block("tool_use", name="list_muscles", id="t1", input={})]),
        _Resp("end_turn", [_Block("text", text="Tu as 0 muscle tracké.")]),
    ])
    res = await agent_chat.run_agent_chat([{"role": "user", "content": "mes muscles ?"}])
    assert res["reply"] == "Tu as 0 muscle tracké."
    assert res["tool_calls"] == ["list_muscles"]
    # 2 appels au modèle : initial + après tool_result.
    assert len(fake.messages.calls) == 2
    # Les specs de tools sont bien passées au 1er appel (14 read+write).
    assert len(fake.messages.calls[0]["tools"]) == 14


async def test_tool_use_loop_write_mutates_db(monkeypatch, agent_seed):
    """Un mark_set_done demandé par le modèle mute réellement la DB de test."""
    set_mcp_context(user_id=agent_seed["user_id"], client_id="in-app-agent",
                    scopes=["sport:read", "sport:write"])
    _patch_client(monkeypatch, [
        _Resp("tool_use", [_Block("tool_use", name="mark_set_done", id="t1",
                                  input={"set_uuid": agent_seed["set_uuid"],
                                         "reps": 12, "weight": 60.0})]),
        _Resp("end_turn", [_Block("text", text="C'est noté : 12 reps à 60 kg.")]),
    ])
    res = await agent_chat.run_agent_chat(
        [{"role": "user", "content": "j'ai fait 12 reps à 60kg sur mon set"}]
    )
    assert res["tool_calls"] == ["mark_set_done"]
    # Vérifie la mutation via le read tool.
    got = await sport_data.get_workout(agent_seed["user_id"], agent_seed["aw_uuid"])
    set0 = got["exercises"][0]["sets"][0]
    assert set0["status"] == "DONE" and set0["reps"] == 12 and set0["weight"] == 60.0


async def test_no_tool_use_direct_reply(monkeypatch, agent_seed):
    """Si le modèle répond direct (pas de tool), on renvoie son texte, 1 appel."""
    set_mcp_context(user_id=agent_seed["user_id"], client_id="in-app-agent",
                    scopes=["sport:read", "sport:write"])
    fake = _patch_client(monkeypatch, [
        _Resp("end_turn", [_Block("text", text="Bonjour, comment puis-je aider ?")]),
    ])
    res = await agent_chat.run_agent_chat([{"role": "user", "content": "salut"}])
    assert res["reply"] == "Bonjour, comment puis-je aider ?"
    assert res["tool_calls"] == []
    assert len(fake.messages.calls) == 1


async def test_max_iterations_forces_final(monkeypatch, agent_seed):
    """Si le modèle rappelle toujours un tool, on coupe et force une réponse."""
    set_mcp_context(user_id=agent_seed["user_id"], client_id="in-app-agent",
                    scopes=["sport:read", "sport:write"])
    # N tours tool_use d'affilée + 1 réponse finale (sans tools) à la sortie.
    scripted = [
        _Resp("tool_use", [_Block("tool_use", name="list_muscles", id=f"t{i}", input={})])
        for i in range(settings.AGENT_MAX_TOOL_ITERATIONS)
    ]
    scripted.append(_Resp("end_turn", [_Block("text", text="Réponse forcée.")]))
    fake = _patch_client(monkeypatch, scripted)
    res = await agent_chat.run_agent_chat([{"role": "user", "content": "boucle"}])
    assert res["reply"] == "Réponse forcée."
    assert len(res["tool_calls"]) == settings.AGENT_MAX_TOOL_ITERATIONS
    # Le dernier appel (forçage) n'a PAS de tools.
    assert "tools" not in fake.messages.calls[-1]


# ============================================================
# Sécurité : isolation cross-user + scope allow-list
# ============================================================


async def test_cross_user_isolation(monkeypatch, agent_seed, test_engine):
    """Un autre user ne peut pas muter le set du user seedé via l'agent."""
    set_mcp_context(user_id=999999, client_id="in-app-agent",
                    scopes=["sport:read", "sport:write"])
    _patch_client(monkeypatch, [
        _Resp("tool_use", [_Block("tool_use", name="mark_set_done", id="t1",
                                  input={"set_uuid": agent_seed["set_uuid"],
                                         "reps": 99, "weight": 999.0})]),
        _Resp("end_turn", [_Block("text", text="ok")]),
    ])
    await agent_chat.run_agent_chat([{"role": "user", "content": "modifie ce set"}])
    # Le set du vrai user n'a PAS été muté à 99/999.
    maker = async_sessionmaker(test_engine, expire_on_commit=False)
    async with maker() as db:
        s = (await db.execute(
            select(ActualWorkoutSet).where(ActualWorkoutSet.uuid == agent_seed["set_uuid"])
        )).scalar_one()
        assert not (s.reps == 99 and s.weight == 999.0)


async def test_destructive_tool_not_exposed():
    """Aucun tool destructive/ops n'est dans l'allow-list agent."""
    assert "delete_actual_workout" not in tool_bridge.AGENT_TOOL_NAMES
    assert "bulk_delete_notifications" not in tool_bridge.AGENT_TOOL_NAMES
    assert "get_service_status" not in tool_bridge.AGENT_TOOL_NAMES
    assert len(tool_bridge.AGENT_TOOL_NAMES) == 14


async def test_execute_tool_rejects_out_of_allowlist(monkeypatch, agent_seed):
    """execute_tool refuse un tool hors allow-list même si le modèle l'invente."""
    set_mcp_context(user_id=agent_seed["user_id"], client_id="in-app-agent",
                    scopes=["sport:read", "sport:write"])
    out = await tool_bridge.execute_tool("delete_actual_workout",
                                         {"workout_uuid": agent_seed["aw_uuid"]})
    assert '"ok": false' in out.lower()
    # La séance existe toujours (jamais supprimée).
    got = await sport_data.get_workout(agent_seed["user_id"], agent_seed["aw_uuid"])
    assert got["found"] is True


# ============================================================
# Contrat HTTP (router)
# ============================================================


async def test_chat_503_without_api_key(client, monkeypatch):
    """ANTHROPIC_API_KEY absente -> 503 (clé jamais dans l'APK)."""
    from tests.conftest import login_headers
    monkeypatch.setattr(settings, "ANTHROPIC_API_KEY", "")
    headers = await login_headers(client, "testuser", "testpass")
    resp = await client.post("/api/v1/agent/chat",
                             json={"messages": [{"role": "user", "content": "hi"}]},
                             headers=headers)
    assert resp.status_code == 503


async def test_chat_401_without_auth(client):
    """Pas de Bearer -> 401 (Depends(get_current_user_id))."""
    resp = await client.post("/api/v1/agent/chat",
                             json={"messages": [{"role": "user", "content": "hi"}]})
    assert resp.status_code == 401


async def test_chat_happy_path_http(client, monkeypatch, agent_seed):
    """Bout-en-bout via HTTP : clé posée + Anthropic mocké -> 200 + reply + toolCalls."""
    from tests.conftest import login_headers
    monkeypatch.setattr(settings, "ANTHROPIC_API_KEY", "test-key")
    _patch_client(monkeypatch, [
        _Resp("tool_use", [_Block("tool_use", name="list_muscles", id="t1", input={})]),
        _Resp("end_turn", [_Block("text", text="Voici tes muscles.")]),
    ])
    headers = await login_headers(client, "testuser", "testpass")
    resp = await client.post(
        "/api/v1/agent/chat",
        json={"messages": [{"role": "user", "content": "mes muscles ?"}]},
        headers=headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["reply"] == "Voici tes muscles."
    # Wire camelCase (politique 17).
    assert body["toolCalls"] == [{"toolName": "list_muscles"}]


async def test_chat_empty_messages_422(client, monkeypatch):
    """messages vide -> 422 (validation Pydantic min_length=1)."""
    from tests.conftest import login_headers
    monkeypatch.setattr(settings, "ANTHROPIC_API_KEY", "test-key")
    headers = await login_headers(client, "testuser", "testpass")
    resp = await client.post("/api/v1/agent/chat",
                             json={"messages": []}, headers=headers)
    assert resp.status_code == 422


def test_rate_limit_applied_on_endpoint():
    """L'endpoint /agent/chat porte bien une limite slowapi (anti cost-bomb).

    slowapi est désactivé globalement pendant la session pytest (conftest), donc
    on ne peut pas provoquer un vrai 429 ; on vérifie que la limite issue de
    settings.AGENT_RATE_LIMIT est enregistrée sur l'endpoint (garde-fou T12)."""
    from app.main import app  # noqa: F401 -- force l'enregistrement des limites
    from app.rate_limit import limiter

    key = "app.routers.agent_router.agent_chat"
    limits = limiter._route_limits.get(key)
    assert limits, "Aucune limite slowapi enregistrée sur /agent/chat"
    assert any("30 per 1 minute" in str(item.limit) for item in limits)
