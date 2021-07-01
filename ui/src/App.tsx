import React from "react";
import { BrowserRouter, Redirect, Route, Switch } from "react-router-dom";
import { AuthAxios, Role } from "./auth";
import { AuthProvider } from "./AuthProvider";
import Admin from "./pages/Admin";
import Company from "./pages/Company";
import Employee from "./pages/Employee";
import { SnackbarProvider } from 'notistack';
import { Grow } from "@material-ui/core";

export const App = () => {
    return <SnackbarProvider anchorOrigin={{horizontal: "center", vertical: "bottom"}} TransitionComponent={Grow as any}>
        <BrowserRouter>
            <Switch>
                <Route path="/admin">
                    <AuthProvider>{
                        ({authFetch}: {authFetch: AuthAxios}) => authFetch.role === Role.Admin
                            ? <Admin authFetch={authFetch}/>
                            : <Redirect to={`/${authFetch.role.toLowerCase()}`} />
                    }</AuthProvider>
                </Route>
                <Route path="/company">
                    <AuthProvider>{
                        ({authFetch}: {authFetch: AuthAxios}) => authFetch.role === Role.Company
                            ? <Company authFetch={authFetch}/>
                            : <Redirect to={`/${authFetch.role.toLowerCase()}`} />
                    }</AuthProvider>
                </Route>
                <Route path="/employee">
                    <AuthProvider>{
                        ({authFetch}: {authFetch: AuthAxios}) => authFetch.role === Role.Employee
                            ? <Employee authFetch={authFetch}/>
                            : <Redirect to={`/${authFetch.role.toLowerCase()}`} />
                    }</AuthProvider>
                </Route>
                <Route path="/">
                    <AuthProvider>{
                        ({authFetch}: {authFetch: AuthAxios}) =>
                            <Redirect to={`/${authFetch.role.toLowerCase()}`} />
                    }</AuthProvider>
                </Route>
            </Switch>
        </BrowserRouter>
    </SnackbarProvider>
};

