import { atom } from "recoil";

export type User = {
    firstname?: string,
    name?: string,
    email: string,
};

export const bearerToken = atom<string | undefined>({
    key: "bearerToken",
    default: undefined
});