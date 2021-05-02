import { AuthenticatedFetch } from "../../atoms/Auth";
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

    export const createApi = (authFetch: AuthenticatedFetch) => ({
        create: (formSession: Create, formId: string): Promise<Full> =>
            authFetch.post(`/api/company/form/${formId}/session`, JSON.stringify(formSession)).then<Full>(b => b.json()),
        update: (formSession: Update) =>
            authFetch.put(`/api/company/session/${formSession.id}`, JSON.stringify(formSession)).then<Full>(b => b.json()),
        delete: (formSessionId: string) => authFetch.delete(`/api/company/session/${formSessionId}`).then(_ => {}),
        list: (pageSize?: number, offset?: number) => 
            authFetch.get(`/api/company/session`).then<Full[]>(b => b.json()),
        getById: (formId: string) => authFetch.get(`/api/company/session/${formId}`).then<Full>(b => b.json()),
    });
};