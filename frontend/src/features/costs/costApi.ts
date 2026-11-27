import { body, exactRequest } from '../../services/commercial';

export type Technique = 'FDM' | 'SUBLIMATION';
export const techniques: Record<Technique, string> = {
  FDM: 'Impresión 3D FDM',
  SUBLIMATION: 'Sublimación',
};
export type Profile = {
  id: number;
  code: string;
  name: string;
  electricityRatePerKwh: string;
  laborRatePerHour: string;
  indirectRatePerHour: string;
  active: boolean;
  version: number;
};
export type Machine = {
  id: number;
  code: string;
  name: string;
  technique: Technique;
  nominalPowerWatts: string | null;
  machineRatePerHour: string;
  active: boolean;
  version: number;
};
type SourceMaterial = {
  materialId: number;
  quantity: string;
  unit: string;
  acceptZeroCost: boolean;
  zeroCostReason: string | null;
};
type Common = {
  jobDescription: string;
  expectedGoodUnits: number;
  costProfileId: number;
  machineId: number;
  productId?: number;
  variantId?: number;
  productiveSeconds: number;
  powerWatts: string | null;
  materials: SourceMaterial[];
  targetMarginPct: string;
  chosenUnitPrice: string | null;
};
export type CostInput = { common: Common; laborSeconds: number } & (
  | {
      printSeconds: number;
      partMassGrams: string;
      supportMassGrams: string | null;
    }
  | {
      cycleCount: number;
      pressSecondsPerCycle: number;
      preparationSeconds: number;
    }
);
type Result = {
  expectedGoodUnits: number;
  totalCost: string;
  unitCost: string;
  productiveSeconds: number;
  indirectCost: string;
};
type CostLine = {
  position: number;
  costType: string;
  description: string;
  quantity: string;
  unit: string;
  appliedRate: string;
  amount: string;
};
type Estimate = {
  targetMarginPct: string;
  suggestedUnitPrice: string;
  chosenUnitPrice: string;
  grossProfitPerUnit: string;
  actualMarginPct: string | null;
  belowCost: boolean;
};
export type Preview = Result & {
  lines: CostLine[];
  price: Estimate;
  previewToken: string;
};
export type HistoryItem = Result & {
  id: number;
  technique: Technique;
  createdAt: string;
  jobDescription: string;
  productId: number | null;
  variantId: number | null;
  commercialSku: string | null;
};
export type HistoryDetail = {
  calculation: HistoryItem;
  lines: CostLine[];
  price: Estimate | null;
  fdm: { printSeconds: number; laborSeconds: number } | null;
  sublimation: {
    cycleCount: number;
    pressSecondsPerCycle: number;
    preparationSeconds: number;
    laborSeconds: number;
  } | null;
};
type HistoryPage = { content: HistoryItem[]; page: number; totalPages: number };

const fields = [
  'electricityRatePerKwh',
  'laborRatePerHour',
  'indirectRatePerHour',
  'nominalPowerWatts',
  'machineRatePerHour',
  'indirectCost',
  'totalCost',
  'unitCost',
  'quantity',
  'appliedRate',
  'amount',
  'targetMarginPct',
  'suggestedUnitPrice',
  'chosenUnitPrice',
  'grossProfitPerUnit',
  'actualMarginPct',
];
const endpoint = (technique: Technique) =>
  technique === 'FDM' ? '/fdm' : '/sublimation';
const read = <T>(token: string, path: string, options?: RequestInit) =>
  exactRequest<T>(token, '/production-costs' + path, fields, options);
export const costApi = {
  profiles: (token: string, signal?: AbortSignal) =>
    read<Profile[]>(token, '/profiles', { signal }),
  machines: (token: string, technique: Technique, signal?: AbortSignal) =>
    read<Machine[]>(token, '/machines?technique=' + technique, { signal }),
  preview: (token: string, technique: Technique, input: CostInput) =>
    read<Preview>(token, endpoint(technique) + '/preview', body(input)),
  save: (
    token: string,
    technique: Technique,
    input: CostInput,
    previewToken: string,
  ) =>
    read<HistoryDetail>(
      token,
      endpoint(technique),
      body({ input, previewToken }),
    ),
  list: (
    token: string,
    page: number,
    technique?: Technique,
    signal?: AbortSignal,
  ) =>
    read<HistoryPage>(
      token,
      '/calculations?' +
        new URLSearchParams({
          page: String(page),
          ...(technique ? { technique } : {}),
        }),
      { signal },
    ),
  detail: (token: string, id: number, signal?: AbortSignal) =>
    read<HistoryDetail>(token, '/calculations/' + id, { signal }),
  saveProfile: (
    token: string,
    data: Omit<Profile, 'id' | 'version'>,
    current?: Profile,
  ) =>
    read<Profile>(
      token,
      '/profiles' + (current ? '/' + current.id : ''),
      body({ ...data, version: current?.version }, current ? 'PUT' : 'POST'),
    ),
  saveMachine: (
    token: string,
    data: Omit<Machine, 'id' | 'version'>,
    current?: Machine,
  ) =>
    read<Machine>(
      token,
      '/machines' + (current ? '/' + current.id : ''),
      body({ ...data, version: current?.version }, current ? 'PUT' : 'POST'),
    ),
};
