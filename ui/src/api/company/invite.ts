import { AuthAxios } from "../../auth";
import { WithId } from "../WithId";

export type FormSessionInvite = {
    user: string,
    team: string
};

export namespace FormSessionInvite {

    export type Create = FormSessionInvite;
    export type Update = WithId<FormSessionInvite>;
    export type Full = WithId<FormSessionInvite>

    export const createApi = (authFetch: AuthAxios) => ({
        create: (formSessionInvite: Create, formSessionId: string): Promise<Full> =>
            authFetch.post<Full>(`/company/session/${formSessionId}/invite`, formSessionInvite).then(b => b.data),
        update: (formSessionInvite: Update) =>
            authFetch.put<Full>(`/company/invite/${formSessionInvite.id}`, formSessionInvite).then(b => b.data),
        delete: (formSessionInviteId: string) => authFetch.delete<void>(`/company/invite/${formSessionInviteId}`),
        listByFormSession: (formSessionId: string, pageSize?: number, offset?: number) =>
            authFetch.get<Full[]>(`/company/session/${formSessionId}/invite`).then(b => b.data),
        getById: (formSessionInviteId: string) => authFetch.get<Full>(`/company/invite/${formSessionInviteId}`).then(b => b.data),
    });
};