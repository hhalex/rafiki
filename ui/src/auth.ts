import axios, { AxiosInstance, AxiosResponse } from "axios";
import { atom, SetterOrUpdater } from "recoil";

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
    get: <T>(url: string) => Promise<T>,
    post: <T>(url: string, body?: any) => Promise<T>,
    put: <T>(url: string, body?: any) => Promise<T>,
    delete: <T>(url: string) => Promise<T>,
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

class IOHttp<T, U> {
    constructor(private p: Promise<T>, private f: (t: T) => U, private err?: (error: any) => any, private catchAll?: (error: any) => any) {}
    public map<V>(g: (t: U) => V) {
        return new IOHttp(this.p, t => g(this.f(t)))
    }
    public flatMap<W, V>(g: (t: U) => IOHttp<W, V>, err?: (error: any) => any) {
        return new IOHttp(this.p.then(t => g(this.f(t)).p, this.err), t => t, err, this.catchAll)
    }
    public get(): Promise<U> {
        return this.p.then(this.f, this.err).catch(this.catchAll)
    }
}

export const createAuthenticatedFetch = (bearerToken: string, role: Role, setter: SetterOrUpdater<AuthAxios | undefined>, snackError: (err: any) => void): AuthAxios => {
    const ax = axios.create({
        baseURL: "/api/",
        headers: { [AuthHeader]: bearerToken }
    });

    const errorHandling = (err: any) => {
        if(err.response.status === 401) {
            TokenAndRole.clean();
            setter(undefined);
        } else snackError(err);
    }

    return {
        get: <T = any>(url: string) => new IOHttp(ax.get<T>(url), r => r.data, errorHandling, snackError).get(),
        post: <T = any>(url: string, body: any) => new IOHttp(ax.post<T>(url, body), r => r.data, errorHandling, snackError).get(),
        put: <T = any>(url: string, body: any) => new IOHttp(ax.put<T>(url, body), r => r.data, errorHandling, snackError).get(),
        delete: <T = any>(url: string) => new IOHttp(ax.delete<T>(url), r => r.data, errorHandling, snackError).get(),
        role
    };
};
