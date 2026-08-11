import { HealthStepCount } from '@core/models/health-step-count.model';
import { HealthMetric } from '@core/models/health-metric.model';
import { HealthGoal } from '@core/models/health-goal.model';
import {
  HR_INTRADAY_TYPE,
  SLEEP_SESSION_TYPE,
  SLOTS_PER_DAY,
  activeHealthGoal,
  averageOfFilledDays,
  clipFutureSlots,
  currentSlot,
  formatHoursMinutes,
  latestMetric,
  latestSlot,
  metricByDayCalendar,
  metricBySlot,
  minutesOfDayToHhmm,
  sleepPhaseTimeline,
  sleepSessionsForDay,
  sleepStagesByDayCalendar,
  slotHhmm,
  stepProgress,
  stepsByDayCalendar,
  stepsBySlot,
  stepsForDay,
  weekDaysEndingToday,
} from './health-aggregations';

function bucket(date: string, bucketStart: string, steps: number): HealthStepCount {
  return { uuid: `${date}-${bucketStart}`, userId: 1, date, bucketStart, steps, updatedAt: null };
}
function metric(
  type: string,
  value: number,
  date: string,
  startTime: string | null = null,
): HealthMetric {
  return { uuid: `${type}-${date}-${startTime}`, userId: 1, type, value, unit: '', date, startTime, updatedAt: null };
}
function goal(type: string, target: number, effectiveFrom: string): HealthGoal {
  return { uuid: `${type}-${effectiveFrom}`, userId: 1, type, target, effectiveFrom, updatedAt: null };
}

describe('health-aggregations', () => {
  describe('stepsForDay', () => {
    it('somme les buckets de la date, ignore les autres jours', () => {
      const buckets = [bucket('2026-07-06', '08:00', 100), bucket('2026-07-06', '08:30', 50), bucket('2026-07-05', '09:00', 999)];
      expect(stepsForDay(buckets, '2026-07-06')).toBe(150);
    });
    it('0 si aucun bucket ce jour', () => {
      expect(stepsForDay([], '2026-07-06')).toBe(0);
    });
  });

  describe('stepsBySlot', () => {
    it('ventile sur 48 tranches de 30 min (08:00 → slot 16, 08:30 → slot 17)', () => {
      const series = stepsBySlot([bucket('2026-07-06', '08:00', 100), bucket('2026-07-06', '08:30', 40)], '2026-07-06');
      expect(series.length).toBe(SLOTS_PER_DAY);
      expect(series[16]).toBe(100);
      expect(series[17]).toBe(40);
      expect(series[0]).toBe(0);
    });
    it('un bucket horaire résiduel tombe dans sa tranche (09:00 → slot 18)', () => {
      expect(stepsBySlot([bucket('2026-07-06', '09:00', 30)], '2026-07-06')[18]).toBe(30);
    });
  });

  describe('clipFutureSlots', () => {
    it('met à zéro les tranches après currentSlot', () => {
      expect(clipFutureSlots([5, 5, 5, 5], 1)).toEqual([5, 5, 0, 0]);
    });
  });

  describe('currentSlot', () => {
    it('08:47 → slot 17 (heure×2 + minute/30)', () => {
      expect(currentSlot(new Date(2026, 6, 6, 8, 47))).toBe(17);
    });
    it('00:00 → slot 0', () => {
      expect(currentSlot(new Date(2026, 6, 6, 0, 0))).toBe(0);
    });
  });

  describe('stepsByDayCalendar', () => {
    it('réserve un slot par jour (0 si absent), ordre conservé', () => {
      const days = ['2026-07-04', '2026-07-05', '2026-07-06'];
      const out = stepsByDayCalendar([bucket('2026-07-04', '10:00', 200), bucket('2026-07-06', '10:00', 300)], days);
      expect(out).toEqual([
        { date: '2026-07-04', value: 200 },
        { date: '2026-07-05', value: 0 },
        { date: '2026-07-06', value: 300 },
      ]);
    });
  });

  describe('metricByDayCalendar', () => {
    it('filtre par type, prend la row la plus tardive par jour, 0 si absent', () => {
      const metrics = [metric('HEART_RATE', 60, '2026-07-06', '08:00'), metric('HEART_RATE', 72, '2026-07-06', '20:00'), metric('SLEEP', 400, '2026-07-06')];
      const out = metricByDayCalendar(metrics, 'HEART_RATE', ['2026-07-05', '2026-07-06']);
      expect(out).toEqual([
        { date: '2026-07-05', value: 0 },
        { date: '2026-07-06', value: 72 },
      ]);
    });
  });

  describe('latestMetric', () => {
    it('renvoie la mesure la plus récente (date puis startTime)', () => {
      const metrics = [metric('SPO2', 96, '2026-07-05', '03:00'), metric('SPO2', 98, '2026-07-06', '02:00'), metric('SPO2', 97, '2026-07-06', '04:00')];
      expect(latestMetric(metrics, 'SPO2')?.value).toBe(97);
    });
    it('null si aucune mesure du type', () => {
      expect(latestMetric([], 'SPO2')).toBeNull();
    });
  });

  describe('metricBySlot (FC intraday)', () => {
    it('ventile les tranches HEART_RATE_INTRADAY par startTime (10:00 → slot 20, 10:30 → slot 21)', () => {
      const metrics = [
        metric(HR_INTRADAY_TYPE, 62, '2026-07-06', '10:00'),
        metric(HR_INTRADAY_TYPE, 88, '2026-07-06', '10:30'),
        metric('HEART_RATE', 70, '2026-07-06'), // moyenne quotidienne (startTime null) : ignorée
      ];
      const series = metricBySlot(metrics, HR_INTRADAY_TYPE, '2026-07-06');
      expect(series.length).toBe(SLOTS_PER_DAY);
      expect(series[20]).toBe(62);
      expect(series[21]).toBe(88);
      expect(series[0]).toBe(0);
    });
    it('ignore les autres jours et les rows sans startTime', () => {
      const metrics = [
        metric(HR_INTRADAY_TYPE, 90, '2026-07-05', '10:00'),
        metric(HR_INTRADAY_TYPE, 75, '2026-07-06', null),
      ];
      expect(metricBySlot(metrics, HR_INTRADAY_TYPE, '2026-07-06').every((v) => v === 0)).toBe(true);
    });
  });

  describe('slotHhmm', () => {
    it('rend l’heure de début de tranche (0 → 00:00, 21 → 10:30, 47 → 23:30)', () => {
      expect(slotHhmm(0)).toBe('00:00');
      expect(slotHhmm(1)).toBe('00:30');
      expect(slotHhmm(21)).toBe('10:30');
      expect(slotHhmm(47)).toBe('23:30');
    });
  });

  describe('latestSlot', () => {
    it('renvoie la dernière tranche renseignée jusqu’à currentSlot', () => {
      const series = [0, 60, 0, 72, 0, 0];
      expect(latestSlot(series, 5)).toEqual({ slot: 3, value: 72 });
    });
    it('ignore les tranches futures (> currentSlot)', () => {
      const series = [0, 60, 0, 99];
      expect(latestSlot(series, 1)).toEqual({ slot: 1, value: 60 });
    });
    it('null si aucune tranche renseignée', () => {
      expect(latestSlot([0, 0, 0], 2)).toBeNull();
    });
  });

  describe('averageOfFilledDays', () => {
    it('moyenne des jours > 0 (ignore les 0)', () => {
      expect(averageOfFilledDays([8000, 0, 0, 8000])).toBe(8000);
    });
    it('null si aucun jour renseigné', () => {
      expect(averageOfFilledDays([0, 0])).toBeNull();
    });
  });

  describe('activeHealthGoal', () => {
    it('prend le plus grand effectiveFrom ≤ jour, du bon type', () => {
      const goals = [goal('STEPS', 8000, '2026-07-01'), goal('STEPS', 10000, '2026-07-05'), goal('WATER_ML', 2000, '2026-07-06')];
      expect(activeHealthGoal(goals, '2026-07-06', 'STEPS')?.target).toBe(10000);
      expect(activeHealthGoal(goals, '2026-07-03', 'STEPS')?.target).toBe(8000);
    });
    it('null si aucun objectif applicable', () => {
      expect(activeHealthGoal([goal('STEPS', 8000, '2026-07-10')], '2026-07-06', 'STEPS')).toBeNull();
    });
  });

  describe('stepProgress', () => {
    it('borne [0, 1]', () => {
      expect(stepProgress(5000, 10000)).toBe(0.5);
      expect(stepProgress(12000, 10000)).toBe(1);
    });
    it('0 si objectif absent ou ≤ 0', () => {
      expect(stepProgress(5000, null)).toBe(0);
      expect(stepProgress(5000, 0)).toBe(0);
    });
  });

  describe('weekDaysEndingToday', () => {
    it('7 jours calendaires finissant aujourd’hui, du plus ancien au plus récent', () => {
      expect(weekDaysEndingToday('2026-07-06', 7)).toEqual([
        '2026-06-30',
        '2026-07-01',
        '2026-07-02',
        '2026-07-03',
        '2026-07-04',
        '2026-07-05',
        '2026-07-06',
      ]);
    });
  });

  describe('formatHoursMinutes', () => {
    it('formate en X h Y min au-delà d’une heure (format du hub Android)', () => {
      expect(formatHoursMinutes(395)).toBe('6 h 35 min');
    });
    it('minutes seules sous une heure', () => {
      expect(formatHoursMinutes(42)).toBe('42 min');
    });
  });

  describe('minutesOfDayToHhmm', () => {
    it('décode les minutes depuis minuit en HH:MM', () => {
      expect(minutesOfDayToHhmm(0)).toBe('00:00');
      expect(minutesOfDayToHhmm(23 * 60 + 47)).toBe('23:47');
    });
  });

  describe('sleepSessionsForDay', () => {
    it('extrait mise au lit + endormissement des sessions du jour, triées', () => {
      const metrics = [
        metric(SLEEP_SESSION_TYPE, 23 * 60 + 12, '2026-07-10', '22:58'), // nuit
        metric(SLEEP_SESSION_TYPE, 14 * 60 + 5, '2026-07-10', '13:50'), // sieste
        metric(SLEEP_SESSION_TYPE, 22 * 60, '2026-07-09', '21:45'), // autre jour
      ];
      expect(sleepSessionsForDay(metrics, '2026-07-10')).toEqual([
        { bedTime: '13:50', asleepTime: '14:05' },
        { bedTime: '22:58', asleepTime: '23:12' },
      ]);
    });
  });

  describe('sleepPhaseTimeline', () => {
    it('interprète ≥ 15h comme la veille au soir et trie par début', () => {
      const metrics = [
        metric('SLEEP_SLICE_LIGHT', 45, '2026-07-10', '00:15'),
        metric('SLEEP_SLICE_DEEP', 45, '2026-07-10', '23:30'),
        metric('SLEEP_SLICE_REM', 30, '2026-07-10', '14:00'), // sieste : même jour
        metric('SLEEP_SLICE_DEEP', 60, '2026-07-09', '23:00'), // autre jour : exclue
      ];
      const out = sleepPhaseTimeline(metrics, '2026-07-10');
      expect(out.map((p) => p.startMin)).toEqual([-30, 15, 840]);
      expect(out.map((p) => p.bucket)).toEqual([0, 1, 2]);
      expect(out.map((p) => p.minutes)).toEqual([45, 45, 30]);
    });
  });

  describe('sleepStagesByDayCalendar', () => {
    it('empile [profond, léger, paradoxal, éveillé] par jour, 0 si absent', () => {
      const metrics = [
        metric('SLEEP_STAGE_DEEP', 80, '2026-07-10'),
        metric('SLEEP_STAGE_LIGHT', 210, '2026-07-10'),
        metric('SLEEP_STAGE_AWAKE', 25, '2026-07-10'),
        metric('SLEEP_STAGE_REM', 60, '2026-07-09'),
      ];
      const result = sleepStagesByDayCalendar(metrics, ['2026-07-09', '2026-07-10']);
      expect(result.hasData).toBe(true);
      expect(result.stacked).toEqual([
        [0, 0, 60, 0],
        [80, 210, 0, 25],
      ]);
    });
    it('hasData=false sans aucune phase', () => {
      expect(sleepStagesByDayCalendar([], ['2026-07-10']).hasData).toBe(false);
    });
  });
});
