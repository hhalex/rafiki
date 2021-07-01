import { AuthAxios } from "../../auth";
import { FormSessionInvite } from "../company/invite";
import { WithId } from "../WithId";
import { InviteAnswer } from "./answer";

export type EmployeeInvite = [string, FormSessionInvite.Update, InviteAnswer.Update]

export namespace EmployeeInvite {

    export type Create = EmployeeInvite;
    export type Update = WithId<EmployeeInvite>;
    export type Full = WithId<InviteAnswer>

    export type Api = ReturnType<typeof createApi>

    export const createApi = (authFetch: AuthAxios) => ({
        getAll: () => authFetch.get<Full[]>(`/employee/invite`),
    });
};