import { WithId } from "./WithId";

export namespace User {
    export type Create = {
        username: string,
        password: string,
        firstname?: string,
        name?: string
    };

    export type Update = WithId<Create>;
}