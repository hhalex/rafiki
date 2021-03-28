import { Dialog, DialogContent } from "@material-ui/core";
import * as React from "react";
import { atom, SetterOrUpdater, useRecoilState } from "recoil";
import { Login } from "../pages/Login";

export type User = {
    firstname?: string,
    name?: string,
    email: string,
};

type SimplifiedFetch = (url: string, body?: string | number) => Promise<Response | void>;

export type AuthenticatedFetch = {
    get: SimplifiedFetch,
    post: SimplifiedFetch,
    delete: SimplifiedFetch,
    put: SimplifiedFetch
}

export const authenticatedFetchAtom = atom<AuthenticatedFetch | undefined>({
    key: "bearerToken",
    default: undefined
});

const AuthHeader = "Authorization";

export const updateAuthenticatedFetchWithResponse = (response: Response, setter: SetterOrUpdater<AuthenticatedFetch | undefined>) => {
    const updatedBearerToken = response.status === 401
        ? undefined
        : response.headers.get(AuthHeader) ?? undefined;
    setter(_ => 
        updatedBearerToken 
            ? createAuthenticatedFetch(updatedBearerToken, setter)
            : undefined
    );
    return response;
}

export const createAuthenticatedFetch = (bearerToken: string, setter: SetterOrUpdater<AuthenticatedFetch | undefined>): AuthenticatedFetch => {
    const customFetch = (method: string) =>
        (url: string, body?: string | number) =>
            fetch(url, { headers: [[AuthHeader, bearerToken]], method, body: body as string })
                .then(response => {
                    if(response.status === 401) setter(undefined);
                    return response;
                }).catch(console.log);

    return {
        get: customFetch("GET"),
        post: customFetch("POST"),
        delete: customFetch("DELETE"),
        put: customFetch("PUT")
    };
};

export const AuthProvider = ({ children }: any) => {

    const [authFetch, setAuthFetch] = useRecoilState(authenticatedFetchAtom);
  
    return authFetch
      ? React.isValidElement<{authFetch: AuthenticatedFetch}>(children)
        ? React.cloneElement(children, {authFetch})
        : React.createElement(children, {authFetch})
      : <Dialog open={true} aria-labelledby="form-dialog-title">
          <DialogContent>
              <Login updateAuthFetchWithResponse={r => updateAuthenticatedFetchWithResponse(r, setAuthFetch)} />
          </DialogContent>
      </Dialog>;
};