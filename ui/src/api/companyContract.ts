import { AuthenticatedFetch } from "../atoms/Auth";
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

    export const createApi = (authFetch: AuthenticatedFetch) => ({
        create: (companyContract: Create) =>
            authFetch.post(`/api/company/${companyContract.company}/contract`, JSON.stringify(companyContract)).then<Full>(b => b.json()),
        update: (companyContract: Update) =>
            authFetch.put(`/api/company/${companyContract.company}/contract/${companyContract.id}`, JSON.stringify(companyContract)).then<Full>(b => b.json()),
        delete: ({ company, id }: Update) =>
            authFetch.delete(`/api/company/${company}/contract/${id}`),
        listByCompany: (companyId: string, pageSize?: number, offset?: number) => 
            authFetch.get(`/api/company/${companyId}/contract`).then<Full[]>(b => b.json()),
        list: (companyId: string, pageSize?: number, offset?: number) => 
            authFetch.get(`/api/company/${companyId}/contract`).then<Full[]>(b => b.json()),
    });
}
