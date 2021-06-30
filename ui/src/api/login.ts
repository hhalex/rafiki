import { Tk } from "../tk";

export type Credentials = { username: string, password: string };

export const createLoginApi = (credentials: Credentials) =>
    Tk.http({
        url: "/login",
        method: "POST",
        body: JSON.stringify(credentials)
    });