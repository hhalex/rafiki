import { Button, Dialog, DialogContent, IconButton } from "@material-ui/core";
import { Clear } from "@material-ui/icons";
import { SnackbarKey, useSnackbar } from "notistack";
import React, { useEffect } from "react";
import { useRecoilState } from "recoil";
import { createLoginApi, Credentials } from "./api/login";
import { authenticatedFetchAtom, createAuthenticatedFetch, updateAuthFetchWithLoginResponse, AuthAxios, TokenAndRole } from "./auth";
import { Login } from "./pages/Login";

export const AuthProvider = ({ children }: any) => {
    const [authFetch, setAuthFetch] = useRecoilState(authenticatedFetchAtom);
    const { enqueueSnackbar, closeSnackbar } = useSnackbar();
    const action = (key: SnackbarKey) =>
        <IconButton onClick={() => closeSnackbar(key)}>
            <Clear />
        </IconButton>
    const snackError = (text: string) => {
        enqueueSnackbar(text, {
            variant: "error",
            preventDuplicate: true,
            action
        });
    }

    // If not authenticated, try using persisted authentication
    useEffect(() => {
        if (!authFetch) {
            const auth = TokenAndRole.get();
            if (auth)
                setAuthFetch(
                    createAuthenticatedFetch(auth.token, auth.role, setAuthFetch, snackError)
                );
        }
    }, [authFetch]);


    const LoginModal = () => {
        const loginApi = (creds: Credentials) => createLoginApi(creds).flatMap(updateAuthFetchWithLoginResponse(setAuthFetch, snackError));
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