export type Credentials = { username: string, password: string };

export const createLoginApi = (updateAuthFetchWithResponse: (r: Response) => Promise<Response>) => 
    (credentials: Credentials) =>
        fetch("/login", {
            method: "POST",
            body: JSON.stringify(credentials)
        }).then(updateAuthFetchWithResponse);