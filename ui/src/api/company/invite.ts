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

    export type Api = ReturnType<typeof createApi>

    export const createApi = (authFetch: AuthAxios) => ({
        create: (formSessionInvite: Create, formSessionId: string) =>
            authFetch.post<Full>(`/company/session/${formSessionId}/invite`, formSessionInvite),
        update: (formSessionInvite: Update) =>
            authFetch.put<Full>(`/company/invite/${formSessionInvite.id}`, formSessionInvite),
        delete: (formSessionInviteId: string) => authFetch.delete<void>(`/company/invite/${formSessionInviteId}`),
        listByFormSession: (formSessionId: string, pageSize?: number, offset?: number) =>
            authFetch.get<Full[]>(`/company/session/${formSessionId}/invite`),
        getById: (formSessionInviteId: string) => authFetch.get<Full>(`/company/invite/${formSessionInviteId}`),
    });
};