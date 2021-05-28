import axios, { AxiosInstance } from "axios";
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

export type AuthAxios = AxiosInstance & { role: Role };

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

export const updateAuthenticatedFetchWithLoginResponse = async (response: Response, setter: SetterOrUpdater<AuthAxios | undefined>) => {
    const updatedBearerToken = response.status === 401
        ? null
        : response.headers.get(AuthHeader);
    const role = Role.fromStr(await response.text());
    setter(_ => {
        if (updatedBearerToken && role) {
            TokenAndRole.persist({ token: updatedBearerToken, role });
            return createAuthenticatedFetch(updatedBearerToken, role, setter)
        } else {
            TokenAndRole.clean()
        }
    });
    return response;
}

export const createAuthenticatedFetch = (bearerToken: string, role: Role, setter: SetterOrUpdater<AuthAxios | undefined>): AuthAxios => {
    const ax = axios.create({
        baseURL: "/api/",
        headers: { [AuthHeader]: bearerToken }
    });

    ax.interceptors.response.use(undefined, err => {
        if(err.response.status === 401) {
            TokenAndRole.clean();
            setter(undefined);
        }
        throw err;
    })

    return {...ax, role } as AuthAxios;
};
