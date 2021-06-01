import { AuthAxios } from "../../auth";
import { WithId } from "../WithId";

export type Form<Tree> = {
    name: string,
    description?: string,
    tree: Tree
};


export namespace Form {
    export type Tree = Tree.Question | Tree.Group | Tree.Text;
    export namespace Tree {
        export namespace Question {
            export namespace Answer {
                export type Numeric = {label?: string, value: number};
                export type FreeText = {label?: string};
            }
            export type Answer = Answer.FreeText | Answer.Numeric;
        }
        export type Question = {label: string, text: string, answers: Question.Answer[]};
        export type Text = {text: string};
        export type Group = {children: Tree[]};
        export type QuestionGroup = {children: Question[]};
    }

    export type Create = Form<null>;
    export type Update = WithId<Form<Tree>>;
    export type Full = WithId<Form<Tree>>

    export type Api = ReturnType<typeof createApi>

    export const createApi = (authFetch: AuthAxios) => ({
        create: (form: Create) =>
            authFetch.post<Full>("/company/form", form),
        update: (form: Update) =>
            authFetch.put<Full>(`/company/form/${form.id}`, form),
        delete: (formId: string) => authFetch.delete<void>(`/company/form/${formId}`),
        list: (pageSize?: number, offset?: number) =>
            authFetch.get<Full[]>(`/company/form`),
        getById: (formId: string) => authFetch.get<Full>(`/company/form/${formId}`),
    });
};