export interface FeatureItem {
  id: number;
  title: string;
  description: string;
  status: string;
  metric: string;
}

export interface KpiItem {
  label: string;
  value: string;
  trend: string;
  tone: string;
}

export interface OperationRecord {
  key: string;
  name: string;
  owner: string;
  status: string;
  metric: string;
  priority: string;
}

export interface OverviewResponse {
  appName: string;
  appCode: string;
  description: string;
  features: FeatureItem[];
  kpis: KpiItem[];
  records: OperationRecord[];
}

export interface Script {
  id: number;
  name: string;
  genre: string;
  difficulty: string;
  durationMinutes: number;
  minPlayers: number;
  maxPlayers: number;
  dmRequirement?: string | null;
  description?: string | null;
  active: boolean;
  createdAt?: string;
}

export interface ScriptForm {
  name: string;
  genre: string;
  difficulty: string;
  durationMinutes: number;
  minPlayers: number;
  maxPlayers: number;
  dmRequirement?: string;
  description?: string;
}

export interface SessionRegistration {
  id: number;
  sessionId: number;
  playerName: string;
  contact?: string | null;
  createdAt?: string;
}

export interface SessionView {
  id: number;
  scriptId: number;
  scriptName: string;
  genre: string;
  difficulty: string;
  startTime: string;
  hostName: string;
  capacity: number;
  registeredCount: number;
  remainingSlots: number;
  full: boolean;
  registrations: SessionRegistration[];
}

export interface SessionForm {
  scriptId: number | null;
  startTime: string;
  hostName: string;
  capacity: number;
}

export interface RegistrationForm {
  playerName: string;
  contact?: string;
}
