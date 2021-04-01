import React from "react";
import { AuthenticatedFetch, AuthProvider, Role } from "./atoms/Auth";
import Admin from "./pages/Admin";

export const App = () => {

    return <AuthProvider>{
        ({authFetch}: {authFetch: AuthenticatedFetch}) => {
            switch (authFetch.role) {
                case Role.Admin:
                    return <Admin authFetch={authFetch}/>
                case Role.Company:
                    return <div>Welcome to you company user!</div>
                default:
                    return <div>There is an error, '{authFetch.role}' is not a valid role.</div>
            }
            
    }}</AuthProvider>
};

