import { Dialog, DialogContent } from "@material-ui/core";
import React, { useEffect } from "react";
import { useRecoilState } from "recoil";
import { createLoginApi } from "./api/login";
import { authenticatedFetchAtom, createAuthenticatedFetch, updateAuthenticatedFetchWithLoginResponse, AuthAxios, TokenAndRole } from "./auth";
import { Login } from "./pages/Login";

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
      ? React.isValidElement<{authFetch: AuthAxios}>(children)
        ? React.cloneElement(children, {authFetch})
        : React.createElement(children, {authFetch})
      : <LoginModal />;
};