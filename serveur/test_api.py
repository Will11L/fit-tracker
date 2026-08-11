import os

import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def auth_headers():
    token_response = client.post("/token", data={"username": "will", "password": os.getenv("SEED_USER_PASSWORD", "changeme")})
    access_token = token_response.json().get("access_token")
    return {"Authorization": f"Bearer {access_token}"}

def test_get_openapi_json():
    headers = auth_headers()
    response = client.get("/openapi.json", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_actual_workouts():
    headers = auth_headers()
    response = client.get("/actual-workouts", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_actual_workouts_workout_id():
    headers = auth_headers()
    response = client.get("/actual-workouts/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_actual_workouts():
    headers = auth_headers()
    response = client.post("/actual-workouts", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_actual_workouts_actual_workout_id_sets():
    headers = auth_headers()
    response = client.get("/actual-workouts/1/sets", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_actual_workout_sets():
    headers = auth_headers()
    response = client.post("/actual-workout-sets", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_actual_workout_sets_set_id():
    headers = auth_headers()
    response = client.delete("/actual-workout-sets/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_available_equipment():
    headers = auth_headers()
    response = client.get("/available-equipment", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_available_equipment_equipment_id_status():
    headers = auth_headers()
    response = client.post("/available-equipment/1/status", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_training_cycles_cycle_id_workouts():
    headers = auth_headers()
    response = client.get("/training-cycles/1/workouts", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_training_cycles_cycle_id_workouts_workout_id():
    headers = auth_headers()
    response = client.delete("/training-cycles/1/workouts/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_training_cycles_cycle_id_workouts_workout_id():
    headers = auth_headers()
    response = client.post("/training-cycles/1/workouts/1", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_equipment():
    headers = auth_headers()
    response = client.get("/equipment", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_equipment():
    headers = auth_headers()
    response = client.post("/equipment", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_exercises_exercise_id_equipment():
    headers = auth_headers()
    response = client.get("/exercises/1/equipment", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_exercises_exercise_id_equipment_equipment_id():
    headers = auth_headers()
    response = client.post("/exercises/1/equipment/1", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_exercises_exercise_id_equipment_equipment_id():
    headers = auth_headers()
    response = client.delete("/exercises/1/equipment/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_exercise_muscles():
    headers = auth_headers()
    response = client.post("/exercise-muscles", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_exercise_muscles():
    headers = auth_headers()
    response = client.get("/exercise-muscles", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_exercises():
    headers = auth_headers()
    response = client.get("/exercises", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_exercises_exercise_id():
    headers = auth_headers()
    response = client.get("/exercises/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_exercises():
    headers = auth_headers()
    response = client.post("/exercises", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_exercises_exercise_id():
    headers = auth_headers()
    response = client.delete("/exercises/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_muscles():
    headers = auth_headers()
    response = client.get("/muscles", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_muscles():
    headers = auth_headers()
    response = client.post("/muscles", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_muscle_weekly_summaries():
    headers = auth_headers()
    response = client.get("/muscle-weekly-summaries", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_planned_workouts_planned_workout_id_exercises():
    headers = auth_headers()
    response = client.get("/planned-workouts/1/exercises", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_planned_workout_exercises():
    headers = auth_headers()
    response = client.post("/planned-workout-exercises", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_planned_workout_exercises_planned_workout_exercise_id():
    headers = auth_headers()
    response = client.delete("/planned-workout-exercises/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_planned_workouts():
    headers = auth_headers()
    response = client.get("/planned-workouts", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_planned_workouts_workout_id():
    headers = auth_headers()
    response = client.get("/planned-workouts/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_planned_workouts():
    headers = auth_headers()
    response = client.post("/planned-workouts", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_planned_workouts_workout_id():
    headers = auth_headers()
    response = client.delete("/planned-workouts/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_sessions():
    headers = auth_headers()
    response = client.get("/sessions", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_sessions():
    headers = auth_headers()
    response = client.post("/sessions", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_sessions_session_id():
    headers = auth_headers()
    response = client.delete("/sessions/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_superset_exercises():
    headers = auth_headers()
    response = client.post("/superset-exercises/", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_superset_exercises_exercise_id():
    headers = auth_headers()
    response = client.get("/superset-exercises/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_superset_exercises_group_group_id():
    headers = auth_headers()
    response = client.get("/superset-exercises/group/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_superset_exercises_exercise_id():
    headers = auth_headers()
    response = client.delete("/superset-exercises/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_superset_groups():
    headers = auth_headers()
    response = client.post("/superset-groups/", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_superset_groups_superset_id():
    headers = auth_headers()
    response = client.get("/superset-groups/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_superset_groups():
    headers = auth_headers()
    response = client.get("/superset-groups/", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_superset_groups_superset_id():
    headers = auth_headers()
    response = client.delete("/superset-groups/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_training_cycles():
    headers = auth_headers()
    response = client.get("/training-cycles", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_training_cycles():
    headers = auth_headers()
    response = client.post("/training-cycles", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_training_cycles_cycle_id_workouts_workout_id():
    headers = auth_headers()
    response = client.post("/training-cycles/1/workouts/1", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_training_cycles_cycle_id_workouts():
    headers = auth_headers()
    response = client.get("/training-cycles/1/workouts", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_training_cycles_cycle_id_workouts_workout_id():
    headers = auth_headers()
    response = client.delete("/training-cycles/1/workouts/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_users():
    headers = auth_headers()
    response = client.post("/users", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_users_user_id():
    headers = auth_headers()
    response = client.get("/users/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_put_users_user_id():
    headers = auth_headers()
    response = client.put("/users/1", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_delete_users_user_id():
    headers = auth_headers()
    response = client.delete("/users/1", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_post_token():
    headers = auth_headers()
    response = client.post("/token", headers=headers, json={"example_key": "example_value"})
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_secure_docs():
    headers = auth_headers()
    response = client.get("/secure-docs", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

def test_get_token_helper():
    headers = auth_headers()
    response = client.get("/token-helper", headers=headers)
    assert response.status_code in [200, 201, 204, 404, 422]

