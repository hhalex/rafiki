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
        /**
         * @param company Data describing the company with its user data.
         * @returns The newly created company and its user.
         */
        create: (company: Create): Promise<Full> =>
            authFetch.post("/api/company", JSON.stringify(company)).then<Full>(b => b.json()),
        /**
         * @param company Data describing the company with its user data.
         * @returns The updated company and its updated user.
         */
        update: (company: Update) =>
            authFetch.put(`/api/company/${company.id}`, JSON.stringify(company)).then<Full>(b => b.json()),
        /**
         * Delete a company instance and its associated user.
         * @param companyId Company Id.
         * @returns An empty promise.
         */
        delete: (companyId: string) => authFetch.delete(`/api/company/${companyId}`).then(_ => {}),
        /**
         * @param {number} [pageSize] Number of records to return.
         * @param {number} [offset] Starting offset.
         * @returns The companies from `offset` to `offset + pageSize`.
         */
        list: (pageSize?: number, offset?: number) => 
            authFetch.get(`/api/company`).then<Full[]>(b => b.json()),
        /**
         * @param companyId Company Id.
         * @returns The company matching `companyId`.
         */
        getById: (companyId: string) => authFetch.get(`/api/company/${companyId}`).then<Full>(b => b.json()),

    });
};