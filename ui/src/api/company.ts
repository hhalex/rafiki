import { AuthenticatedFetch } from "../atoms/Auth";
import { CreateUser } from "./user";
import { WithId } from "./WithId";

export type Company<User> = {
    name: string,
    rh_user: User
};

export type CreateCompany = Company<CreateUser>;
export type UpdateCompany = WithId<CreateCompany>;
export type FullCompany = WithId<Company<WithId<CreateUser>>>

export const createCompanyApi = (authFetch: AuthenticatedFetch) => ({
    create: (company: CreateCompany) =>
        authFetch.post("/api/company", JSON.stringify(company)).then<FullCompany>(b => b.json()),
    update: (company: UpdateCompany) =>
        authFetch.put(`/api/company/${company.id}`, JSON.stringify(company)).then<FullCompany>(b => b.json()),
    delete: (id: string) =>
        authFetch.delete(`/api/company/${id}`),
    list: (pageSize?: number, offset?: number) => 
        authFetch.get(`/api/company`).then<FullCompany[]>(b => b.json()),
    getById: (id: string) => authFetch.get(`/api/company/${id}`).then<FullCompany>(b => b.json()),

});