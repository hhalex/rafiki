import { AuthAxios } from "../../auth";
import { WithId } from "../WithId";

export type FormSession = {
    companyContractId: string,
    formId: string,
    name: string,
    startDate?: Date,
    endDate?: Date,
};

export namespace FormSession {

    export type Create = FormSession;
    export type Update = WithId<FormSession>;
    export type Full = WithId<FormSession>

    export type Api = ReturnType<typeof createApi>

    export const createApi = (authFetch: AuthAxios) => ({
        create: (formSession: Create, formId: string): Promise<Full> =>
            authFetch.post<Full>(`/company/form/${formId}/session`, formSession),
        start: (formSessionId: string) =>
            authFetch.put<Full>(`/company/session/${formSessionId}/start`),
        finish: (formSessionId: string) =>
            authFetch.put<Full>(`/company/session/${formSessionId}/finish`),
        update: (formSession: Update) =>
            authFetch.put<Full>(`/company/session/${formSession.id}`, formSession),
        delete: (formSessionId: string) => authFetch.delete(`/company/session/${formSessionId}`),
        list: (pageSize?: number, offset?: number) =>
            authFetch.get<Full[]>(`/company/session`),
        getById: (formId: string) => authFetch.get<Full>(`/company/session/${formId}`),
    });
};