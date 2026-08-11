#!/usr/bin/env python3
import os
import sys
import argparse
import inspect
import importlib.util

from sqlalchemy import Column
from sqlalchemy.orm import DeclarativeMeta
from sqlalchemy.sql.schema import ForeignKey

EXCLUDED_FILES = {"__init__.py"}

def load_module_from_path(path: str):
    module_name = os.path.splitext(os.path.basename(path))[0]
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module

def iter_model_classes(module):
    for _, obj in inspect.getmembers(module, inspect.isclass):
        if isinstance(obj, DeclarativeMeta):
            # Évite certaines classes internes éventuelles
            if getattr(obj, "__tablename__", None):
                yield obj

def safe_fk_target(fk: ForeignKey) -> str:
    """
    Return FK target without forcing SQLAlchemy to resolve referenced tables.
    """
    # fk.target_fullname is safe: e.g. "users.id"
    target = getattr(fk, "target_fullname", None)
    if target:
        return target
    # fallback (should be rare)
    try:
        return str(fk.column)  # may raise if unresolved
    except Exception:
        return "<unresolved>"

def describe_column(col: Column) -> str:
    parts = []

    # Type
    try:
        parts.append(col.type.__class__.__name__)
    except Exception:
        parts.append("<?>")

    # PK / FK
    if getattr(col, "primary_key", False):
        parts.append("PK")

    fks = list(getattr(col, "foreign_keys", []) or [])
    if fks:
        for fk in fks:
            target = safe_fk_target(fk)
            ondelete = getattr(fk, "ondelete", None)
            parts.append(f"FK -> {target}" + (f" (ondelete={ondelete})" if ondelete else ""))

    # Constraints
    # nullable can be None sometimes (treated as True-ish). We'll show only if explicitly False.
    if getattr(col, "nullable", True) is False:
        parts.append("NOT NULL")

    if getattr(col, "unique", False):
        parts.append("UNIQUE")
    if getattr(col, "index", False):
        parts.append("INDEX")

    # Length (String, etc.)
    if hasattr(col.type, "length") and col.type.length is not None:
        parts.append(f"length={col.type.length}")

    # Default
    default_obj = getattr(col, "default", None)
    if default_obj is not None:
        arg = getattr(default_obj, "arg", None)
        if callable(arg):
            parts.append(f"default={getattr(arg, '__name__', 'callable')}")
        else:
            parts.append(f"default={arg!r}")

    return ", ".join(parts)

def print_model(model):
    print(f"\n{model.__name__}")
    tablename = getattr(model, "__tablename__", "<no tablename>")
    print(f"  table: {tablename}")

    # __table__ peut ne pas être prêt si le modèle est “weird”, donc on sécurise
    table = getattr(model, "__table__", None)
    if table is None:
        print("  [!] Pas de __table__ (modèle non mappé ?)")
        return

    for col in table.columns:
        desc = describe_column(col)
        print(f"  - {col.name}: {desc}")

def walk_models(models_path: str):
    for root, dirs, files in os.walk(models_path):
        # Ignore common cache dirs
        dirs[:] = [d for d in dirs if d != "__pycache__"]

        for file in sorted(files):
            if not file.endswith(".py") or file in EXCLUDED_FILES:
                continue

            path = os.path.join(root, file)
            rel = os.path.relpath(path, start=models_path)
            print(f"\n{os.path.join(models_path, rel)}")

            try:
                module = load_module_from_path(path)
            except Exception as e:
                print(f"[!] Impossible de charger {path}: {e}")
                continue

            found = False
            for model in iter_model_classes(module):
                found = True
                try:
                    print_model(model)
                except Exception as e:
                    print(f"[!] Erreur en inspectant {model}: {e}")

            if not found:
                print("[i] Aucun modèle SQLAlchemy détecté dans ce fichier.")

def main():
    parser = argparse.ArgumentParser(description="Afficher les modèles SQLAlchemy et leurs champs")
    parser.add_argument("--models-path", default="app/models", help="Chemin vers le dossier des models")
    parser.add_argument("--project-root", default=".", help="Racine du projet (ajoutée au PYTHONPATH)")
    args = parser.parse_args()

    project_root = os.path.abspath(args.project_root)
    if project_root not in sys.path:
        sys.path.insert(0, project_root)

    print(args.models_path)
    walk_models(args.models_path)

if __name__ == "__main__":
    main()
