import { Dialog, DialogContent } from "@material-ui/core";
import * as React from "react";
import { useEffect } from "react";
import { atom, SetterOrUpdater, useRecoilState } from "recoil";
import { createLoginApi } from "../api/login";
import { Login } from "../pages/Login";

export type User = {
    firstname?: string,
    name?: string,
    email: string,
};

/**
 * Produces an authenticated http request.
 * @param {string} url The endpoint to call.
 * @param {string} [body] The body of the http request.
 * @returns {Promise<Response>} A promise of the http response.
 */
type SimplifiedFetch = (url: string, body?: string | number) => Promise<Response>;

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

export type AuthenticatedFetch = {
    get: SimplifiedFetch,
    post: SimplifiedFetch,
    delete: SimplifiedFetch,
    put: SimplifiedFetch,
    role: Role
};

export const authenticatedFetchAtom = atom<AuthenticatedFetch | undefined>({
    key: "bearerToken",
    default: undefined
});

type TokenAndRole = {
    token: string,
    role: Role
};

namespace TokenAndRole {
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

export const updateAuthenticatedFetchWithLoginResponse = async (response: Response, setter: SetterOrUpdater<AuthenticatedFetch | undefined>) => {
    const updatedBearerToken = response.status === 401
        ? null
        : response.headers.get(AuthHeader);
    const role = Role.fromStr(await response.json());
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

export const createAuthenticatedFetch = (bearerToken: string, role: Role, setter: SetterOrUpdater<AuthenticatedFetch | undefined>): AuthenticatedFetch => {
    const customFetch = (method: string) =>
        (url: string, body?: string | number) =>
            fetch(url, { headers: [[AuthHeader, bearerToken]], method, body: body as string })
                .then(response => {
                    if(response.status === 401) {
                        TokenAndRole.clean();
                        setter(undefined);
                    }
                    return response;
                });

    return {
        get: customFetch("GET"),
        post: customFetch("POST"),
        delete: customFetch("DELETE"),
        put: customFetch("PUT"),
        role
    };
};

export const AuthProvider = ({ children }: any) => {
    const [authFetch, setAuthFetch] = useRecoilState(authenticatedFetchAtom);

    // If not authenticated, try using persisted authentication
    useEffect(() => {
        if (!authFetch) {
            const auth = TokenAndRole.get();
            if (auth)
                setAuthFetch(
                    createAuthenticatedFetch(auth.token, auth.role, setAuthFetch)
                );
        }
    }, [authFetch]);


    const LoginModal = () => {
        const loginApi = createLoginApi(r => updateAuthenticatedFetchWithLoginResponse(r, setAuthFetch));
        return <Dialog open={true} aria-labelledby="form-dialog-title">
            <DialogContent>
                <Login api={loginApi} />
            </DialogContent>
        </Dialog>;
    }
    
    return authFetch
      ? React.isValidElement<{authFetch: AuthenticatedFetch}>(children)
        ? React.cloneElement(children, {authFetch})
        : React.createElement(children, {authFetch})
      : <LoginModal />;
};