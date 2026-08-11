from .actual_workout_exercise import ActualWorkoutExercise
from .actual_workout import ActualWorkout
from .actual_workout_set import ActualWorkoutSet
from .available_equipment import AvailableEquipment
from .cycle_workout import CycleWorkout
from .equipment import Equipment
from .exercise_equipment import ExerciseEquipment
from .exercise_muscle import ExerciseMuscle
from .exercise import Exercise
from .food import Food
from .food_portion import FoodPortion
from .health_goal import HealthGoal
from .health_metric import HealthMetric
from .health_step_count import HealthStepCount
from .meal import Meal
from .meal_entry import MealEntry
from .meal_preset import MealPreset
from .muscle_goal import MuscleGoal
from .muscle import Muscle
from .notification import Notification
from .nutrition_goal import NutritionGoal
from .planned_workout_exercise import PlannedWorkoutExercise
from .planned_workout import PlannedWorkout
from .quote import Quote
from .recipe import Recipe
from .recipe_ingredient import RecipeIngredient
from .refresh_token import RefreshToken
from .routine_period import RoutinePeriod
from .superset_exercise import SupersetExercise
from .superset_group import SupersetGroup
from .task import Task
from .task_check import TaskCheck
from .training_cycle import TrainingCycle
from .user import User
from .water_intake import WaterIntake

# MCP scaffold 2026-05-27 — enregistre MCPSession/MCPAuditLog/MCPOAuthCode/MCPClient
# dans Base.metadata pour qu'Alembic + create_all les voient.
from app.mcp import models as _mcp_models  # noqa: F401