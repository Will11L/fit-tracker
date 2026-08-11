/** MuscleGoal — forme wire (objectif de volume hebdomadaire par muscle). */
export interface MuscleGoal {
  uuid: string;
  userId: number;
  muscleUUID: string;
  priority: string;
  done: number;
  target: string;
  weekISO: string;
  status: string;
  addedManually: boolean;
  updatedAt: string | null;
}

export interface LocalMuscleGoal extends MuscleGoal {
  synced: boolean;
  pendingDeletion: boolean;
}
