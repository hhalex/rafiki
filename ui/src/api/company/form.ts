import { AuthenticatedFetch } from "../../atoms/Auth";
import { WithId } from "../WithId";

export type Form<Tree> = {
    name: string,
    description?: string,
    tree: Tree
};


export namespace Form {
    
    export type Tree = Tree.Question | Tree.Group | Tree.Text;
    export namespace Tree {
        export type Question = {label: string, text: string};
        export type Text = {text: string};
        export type Group = {children: Tree[]};
    }

    export type Create = Form<null>;
    export type Update = WithId<Form<Tree>>;
    export type Full = WithId<Form<Tree>>

    export const createApi = (authFetch: AuthenticatedFetch) => ({
        create: (form: Create): Promise<Full> =>
            authFetch.post("/api/company/form", JSON.stringify(form)).then<Full>(b => b.json()),
        update: (form: Update) =>
            authFetch.put(`/api/company/form/${form.id}`, JSON.stringify(form)).then<Full>(b => b.json()),
        delete: (formId: string) => authFetch.delete(`/api/company/form/${formId}`).then(_ => {}),
        list: (pageSize?: number, offset?: number) => 
            authFetch.get(`/api/company/form`).then<Full[]>(b => b.json()),
        getById: (formId: string) => authFetch.get(`/api/company/form/${formId}`).then<Full>(b => b.json()),
    });
};