import React from "react";
import { BrowserRouter, Redirect, Route, Switch } from "react-router-dom";
import { AuthenticatedFetch, AuthProvider, Role } from "./atoms/Auth";
import Admin from "./pages/Admin";


export const App = () => {
    return <BrowserRouter>
        <Switch>
            <Route path="/admin">
                <AuthProvider>{
                    ({authFetch}: {authFetch: AuthenticatedFetch}) => authFetch.role === Role.Admin
                        ? <Admin authFetch={authFetch}/>
                        : <Redirect to={`/${authFetch.role.toLowerCase()}`} />
                }</AuthProvider>
            </Route>
            <Route path="/company">
                <AuthProvider>{
                    ({authFetch}: {authFetch: AuthenticatedFetch}) => authFetch.role === Role.Company
                        ? <div>Welcome to you company user!</div>
                        : <Redirect to={`/${authFetch.role.toLowerCase()}`} />
                }</AuthProvider>
            </Route>
            <Route path="/">
                <AuthProvider>{
                    ({authFetch}: {authFetch: AuthenticatedFetch}) => 
                        <Redirect to={`/${authFetch.role.toLowerCase()}`} />
                }</AuthProvider>
            </Route>
        </Switch>
    </BrowserRouter>
};

