export type SubmitForm = (
  path: string,
  data: unknown,
  reset: () => void,
  method?: 'POST' | 'PUT',
) => Promise<void>;
