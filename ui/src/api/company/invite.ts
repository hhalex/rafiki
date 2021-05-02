import { AuthenticatedFetch } from "../../atoms/Auth";
import { WithId } from "../WithId";

export type FormSessionInvite = {
    user: string,
    team: string
};

export namespace FormSessionInvite {

    export type Create = FormSessionInvite;
    export type Update = WithId<FormSessionInvite>;
    export type Full = WithId<FormSessionInvite>

    export const createApi = (authFetch: AuthenticatedFetch) => ({
        create: (formSessionInvite: Create, formSessionId: string): Promise<Full> =>
            authFetch.post(`/api/company/session/${formSessionId}/invite`, JSON.stringify(formSessionInvite)).then<Full>(b => b.json()),
        update: (formSessionInvite: Update) =>
            authFetch.put(`/api/company/invite/${formSessionInvite.id}`, JSON.stringify(formSessionInvite)).then<Full>(b => b.json()),
        delete: (formSessionInviteId: string) => authFetch.delete(`/api/company/invite/${formSessionInviteId}`).then(_ => {}),
        listByFormSession: (formSessionId: string, pageSize?: number, offset?: number) => 
            authFetch.get(`/api/company/session/${formSessionId}/invite`).then<Full[]>(b => b.json()),
        getById: (formSessionInviteId: string) => authFetch.get(`/api/company/invite/${formSessionInviteId}`).then<Full>(b => b.json()),
    });
};