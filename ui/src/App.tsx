import React from "react";
import { Login } from "./pages/Login";
import { useRecoilValue } from "recoil";
import { bearerToken } from "./atoms/auth";
import { Admin } from "./pages/Admin";

export const App = () => {
    const bt = useRecoilValue(bearerToken);
    return bt 
        ? <Admin bt={bt}/>
        : <Login />
};
