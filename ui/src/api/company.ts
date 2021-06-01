import { AuthAxios } from "../auth";
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

    export const createApi = (authFetch: AuthAxios) => ({
        /**
         * @param company Data describing the company with its user data.
         * @returns The newly created company and its user.
         */
        create: (company: Create) =>
            authFetch.post<Full>("/admin/company", company),
        /**
         * @param company Data describing the company with its user data.
         * @returns The updated company and its updated user.
         */
        update: (company: Update) =>
            authFetch.put<Full>(`/admin/company/${company.id}`, company),
        /**
         * Delete a company instance and its associated user.
         * @param companyId Company Id.
         * @returns An empty promise.
         */
        delete: (companyId: string) => authFetch.delete<void>(`/admin/company/${companyId}`),
        /**
         * @param {number} [pageSize] Number of records to return.
         * @param {number} [offset] Starting offset.
         * @returns The companies from `offset` to `offset + pageSize`.
         */
        list: (pageSize?: number, offset?: number) =>
            authFetch.get<Full[]>(`/admin/company`),
        /**
         * @param companyId Company Id.
         * @returns The company matching `companyId`.
         */
        getById: (companyId: string) => authFetch.get<Full>(`/admin/company/${companyId}`),

    });
};