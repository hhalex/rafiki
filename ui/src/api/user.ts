import { WithId } from "./WithId";

export type CreateUser = {
    username: string,
    password: string,
    firstname?: string,
    name?: string
};

export type UpdateUser = WithId<CreateUser>;