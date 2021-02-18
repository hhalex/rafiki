import React from "react";
import { AuthenticatedFetch, AuthProvider } from "./atoms/Auth";
import Admin from "./pages/Admin";

export const App = () => {

    return <AuthProvider>{
        ({authFetch}: {authFetch: AuthenticatedFetch}) => {
            return <Admin authFetch={authFetch}/>
    }}</AuthProvider>
};

