import { atom, SetterOrUpdater } from "recoil";
import { NudeTk, Tk } from "./tk";

export type User = {
    firstname?: string,
    name?: string,
    email: string,
};

export enum Role {
    Admin = "Admin",
    Company = "Company",
    Employee = "Employee"
};

export namespace Role {
    export const fromStr = (str: string | null): Role | null => {
        switch (str) {
            case Role.Admin:
            case Role.Company:
            case Role.Employee:
                return str as Role;
            default:
                return null;
        }
    }
}

export type AuthAxios = {
    get: <T>(url: string) => Tk<T>,
    post: <T>(url: string, body?: any) => Tk<T>,
    put: <T>(url: string, body?: any) => Tk<T>,
    delete: <T>(url: string) => Tk<T>,
    role: Role
};

export const authenticatedFetchAtom = atom<AuthAxios | undefined>({
    key: "bearerToken",
    default: undefined
});

type TokenAndRole = {
    token: string,
    role: Role
};

export namespace TokenAndRole {
    const tokenKey = "RELIONS_AUTH_TOKEN";
    const roleKey = "RELIONS_AUTH_ROLE";
    /**
     * Try extract authentication token and role from local storage
     * @returns Properly formatted authentication token & role, or null in case of error
     */
    export const get = (): TokenAndRole | null => {
        const token = localStorage.getItem(tokenKey);
        const role = Role.fromStr(localStorage.getItem(roleKey));
        return token && role ? { token, role } : null;
    };
    /**
     * Store authentication token and role into the local storage
     */
    export const persist = ({token, role}: TokenAndRole) => {
        localStorage.setItem(tokenKey, token);
        localStorage.setItem(roleKey, role);
    };
    /**
     * Remove persisted authentication token & role from local storage
     */
    export const clean = () => {
        localStorage.removeItem(tokenKey);
        localStorage.removeItem(roleKey);
    };
}

const AuthHeader = "Authorization";

export const updateAuthenticatedFetchWithLoginResponse = async (response: Response, setter: SetterOrUpdater<AuthAxios | undefined>, snackError: (err: any) => void) => {
    const updatedBearerToken = response.status === 401
        ? null
        : response.headers.get(AuthHeader);
    const role = Role.fromStr(await response.text());
    setter(_ => {
        if (updatedBearerToken && role) {
            TokenAndRole.persist({ token: updatedBearerToken, role });
            return createAuthenticatedFetch(updatedBearerToken, role, setter, snackError)
        } else {
            TokenAndRole.clean()
        }
    });
    return response;
}


export const createAuthenticatedFetch = (bearerToken: string, role: Role, setter: SetterOrUpdater<AuthAxios | undefined>, snackError: (err: string) => void): AuthAxios => {
    const headers = { [AuthHeader]: bearerToken };

    const happyPath = <T>(res: Response): NudeTk<T> => ({
        run: <U>(then: ((v: T) => U)) => {
            if (res.status >= 200 && res.status < 300)
                Tk.Pipe.toJson<T>(res, snackError).run(then)
            // Authentication issue, must login again
            else if(res.status === 401) {
                TokenAndRole.clean();
                setter(undefined);
            } else res.text().then(snackError)
            return {
                cancel: () => false
            };
        }
    });

    return {
        get: <T = any>(url: string) => Tk.http({ url: `/api${url}`, headers }, snackError).flatMap<T>(happyPath),
        post: <T = any>(url: string, body: any) => Tk.http({ url: `/api${url}`, body, headers, method: "POST" }, snackError).flatMap<T>(happyPath),
        put: <T = any>(url: string, body: any) => Tk.http({ url: `/api${url}`, body, headers, method: "PUT" }, snackError).flatMap<T>(happyPath),
        delete: <T = any>(url: string) => Tk.http({ url: `/api${url}`, headers, method: "DELETE" }, snackError).flatMap<T>(happyPath),
        role
    };
};
