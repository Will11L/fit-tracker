import { Routes } from '@angular/router';
import { authGuard } from '@core/auth/auth.guard';
import { readStartScreenRoute } from './features/settings/settings-store';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/app-shell').then((m) => m.AppShell),
    children: [
      // Page d'accueil au démarrage choisie dans Paramètres (≈ StartScreen Android).
      { path: '', pathMatch: 'full', redirectTo: () => readStartScreenRoute() },
      {
        path: 'home',
        loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
      },
      {
        path: 'exercises',
        loadComponent: () =>
          import('./features/exercises/exercises-page').then((m) => m.ExercisesPage),
      },
      // Deep link conservé : ouvre la page combinée avec l'exercice présélectionné.
      {
        path: 'exercise/:uuid',
        loadComponent: () =>
          import('./features/exercises/exercises-page').then((m) => m.ExercisesPage),
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/profile/profile-page').then((m) => m.ProfilePage),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings-page').then((m) => m.SettingsPage),
      },
      {
        path: 'sync',
        loadComponent: () =>
          import('./features/settings/sync-settings-page').then((m) => m.SyncSettingsPage),
      },
      {
        path: 'showcase',
        loadComponent: () => import('./showcase/showcase').then((m) => m.Showcase),
      },
      {
        path: 'muscles',
        loadComponent: () => import('./features/muscles/muscles-page').then((m) => m.MusclesPage),
      },
      {
        path: 'materiel',
        loadComponent: () =>
          import('./features/equipment/materiel-page').then((m) => m.MaterielPage),
      },
      {
        path: 'quotes',
        loadComponent: () => import('./features/quotes/quotes-page').then((m) => m.QuotesPage),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications-page').then((m) => m.NotificationsPage),
      },
      {
        path: 'planning',
        loadComponent: () =>
          import('./features/planning/planning-page').then((m) => m.PlanningPage),
      },
      // Calendrier + Objectifs : page combinée 2 colonnes (/goals redirige ici).
      {
        path: 'calendar',
        loadComponent: () =>
          import('./features/calendar/calendar-goals-page').then((m) => m.CalendarGoalsPage),
      },
      // Page Séance autonome (item drawer « Séance ») : séance du jour ou fallback, contenu factorisé
      // (TodaySessionPage) partagé avec l'onglet Séance de l'Accueil.
      {
        path: 'seance',
        loadComponent: () =>
          import('./features/session/today-session-page').then((m) => m.TodaySessionPage),
      },
      // Deep link d'une séance précise (conservé) : ouvre la page séance par uuid.
      {
        path: 'session/:uuid',
        loadComponent: () => import('./features/session/session-page').then((m) => m.SessionPage),
      },
      {
        path: 'stats',
        loadComponent: () => import('./features/stats/stats-page').then((m) => m.StatsPage),
      },
      // Ancienne route Objectifs : la page combinée vit sur /calendar (route canonique).
      { path: 'goals', redirectTo: 'calendar' },
      {
        path: 'routines',
        loadComponent: () =>
          import('./features/routines/routines-page').then((m) => m.RoutinesPage),
      },
      {
        path: 'nutrition',
        loadComponent: () =>
          import('./features/nutrition/nutrition-page').then((m) => m.NutritionPage),
      },
      {
        path: 'nutrition/foods',
        loadComponent: () =>
          import('./features/nutrition/food-catalogue-page').then((m) => m.FoodCataloguePage),
      },
      {
        path: 'nutrition/stats',
        loadComponent: () =>
          import('./features/nutrition/nutrition-stats-page').then((m) => m.NutritionStatsPage),
      },
      {
        path: 'nutrition/recipes',
        loadComponent: () => import('./features/nutrition/recipes-page').then((m) => m.RecipesPage),
      },
      {
        path: 'nutrition/goals',
        loadComponent: () =>
          import('./features/nutrition/nutrition-goals-page').then((m) => m.NutritionGoalsPage),
      },
      {
        path: 'chrono',
        loadComponent: () => import('./features/chrono/chrono-page').then((m) => m.ChronoPage),
      },
      // Santé — hub lecture seule (miroir du hub Android), source Health Connect via le serveur.
      {
        path: 'health',
        loadComponent: () => import('./features/health/health-page').then((m) => m.HealthPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
