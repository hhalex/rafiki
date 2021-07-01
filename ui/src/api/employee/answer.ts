import { AuthAxios } from "../../auth";
import { WithId } from "../WithId";

export type InviteAnswer = {
    values: {
        [k: string]: string | null
    }
};

export namespace InviteAnswer {

    export type Create = InviteAnswer;
    export type Update = WithId<InviteAnswer>;
    export type Full = WithId<InviteAnswer>

    export type Api = ReturnType<typeof createApi>

    export const createApi = (authFetch: AuthAxios) => ({
        create: (inviteAnswer: Create, sessionInviteId: string) =>
            authFetch.post<Full>(`/employee/invite/${sessionInviteId}/answer`, inviteAnswer),
        update: (inviteAnswer: Update) =>
            authFetch.put<Full>(`/employee/invite/${inviteAnswer.id}/answer`, inviteAnswer),
        delete: (sessionInviteId: string) => authFetch.delete<void>(`/employee/invite/${sessionInviteId}/answer`),
    });
};