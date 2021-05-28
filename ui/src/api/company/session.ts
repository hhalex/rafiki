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

    export const createApi = (authFetch: AuthAxios) => ({
        create: (formSession: Create, formId: string): Promise<Full> =>
            authFetch.post<Full>(`/company/form/${formId}/session`, formSession).then(b => b.data),
        start: (formSessionId: string) =>
            authFetch.put<Full>(`/company/session/${formSessionId}/start`).then(b => b.data),
        finish: (formSessionId: string) =>
            authFetch.put<Full>(`/company/session/${formSessionId}/finish`).then(b => b.data),
        update: (formSession: Update) =>
            authFetch.put<Full>(`/company/session/${formSession.id}`, formSession).then(b => b.data),
        delete: (formSessionId: string) => authFetch.delete(`/company/session/${formSessionId}`),
        list: (pageSize?: number, offset?: number) =>
            authFetch.get<Full[]>(`/company/session`).then(b => b.data),
        getById: (formId: string) => authFetch.get(`/company/session/${formId}`).then(b => b.data),
    });
};