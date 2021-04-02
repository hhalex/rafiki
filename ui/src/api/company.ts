import { AuthenticatedFetch } from "../atoms/Auth";
import { User } from "./user";
import { WithId } from "./WithId";

export type Company<User> = {
    name: string,
    rh_user: User
};
export namespace Company {
    export type Create = Company<User.Create>;
    export type Update = WithId<Create>;
    export type Full = WithId<Company<WithId<User.Create>>>

    export const createApi = (authFetch: AuthenticatedFetch) => ({
        create: (company: Create) =>
            authFetch.post("/api/company", JSON.stringify(company)).then<Full>(b => b.json()),
        update: (company: Update) =>
            authFetch.put(`/api/company/${company.id}`, JSON.stringify(company)).then<Full>(b => b.json()),
        delete: (id: string) =>
            authFetch.delete(`/api/company/${id}`),
        list: (pageSize?: number, offset?: number) => 
            authFetch.get(`/api/company`).then<Full[]>(b => b.json()),
        getById: (id: string) => authFetch.get(`/api/company/${id}`).then<Full>(b => b.json()),

    });
};