import { AuthAxios } from "../auth";
import { WithId } from "./WithId";

export type CompanyContract<Company> = {
    company: Company,
    kind: CompanyContract.Kind
};

export namespace CompanyContract {
    export const enum Kind {
        Unlimited = "unlimited",
        OneShot = "oneshot"
    }

    export type Create = CompanyContract<string>;
    export type Update = WithId<Create>;
    export type Full = WithId<CompanyContract<string>>

    export const createApi = (authFetch: AuthAxios) => ({
        create: (companyContract: Create) =>
            authFetch.post<Full>(`/company/${companyContract.company}/contract`, companyContract).then(b => b.data),
        update: (companyContract: Update) =>
            authFetch.put<Full>(`/company/${companyContract.company}/contract/${companyContract.id}`, companyContract).then(b => b.data),
        delete: ({ company, id }: Update) =>
            authFetch.delete<void>(`/company/${company}/contract/${id}`),
        listByCompany: (companyId: string, pageSize?: number, offset?: number) =>
            authFetch.get<Full[]>(`/company/${companyId}/contract`).then(b => b.data),
        list: (companyId: string, pageSize?: number, offset?: number) =>
            authFetch.get<Full[]>(`/company/${companyId}/contract`).then(b => b.data),
    });
}
