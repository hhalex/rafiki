import React, { useEffect } from 'react';
import { makeStyles, Theme } from '@material-ui/core/styles';
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';
import { Link, Route, Switch, useLocation, useRouteMatch } from "react-router-dom";
import { AuthenticatedFetch } from '../atoms/Auth';
import { Typography } from '@material-ui/core';

const useStyles = makeStyles((theme: Theme) => ({
  root: {

    backgroundColor: theme.palette.background.paper,

    width: "100%",
    maxWidth: 1024,
    minWidth: 640,

    margin: "auto",

  },
  header: {
    display: "flex",
    height: "25vh",
    alignItems: "center",
    justifyContent: "center"
  },
  body: {
    height: "60vh",
    display: 'flex',
    flexGrow: 1,

  },
  tabs: {
    borderRight: `1px solid ${theme.palette.divider}`,
  },
  panel: {
    flexGrow: 1
  }
}));

const NavTab = ({to, label}: {to: string, label: string}) => {
  const { path, url } = useRouteMatch();
  const { pathname } = useLocation();
  const selected = `${path}${to}` == pathname;
  return <Link to={`${url}${to}`} ><Tab label={label} selected={selected} /></Link>
};


export default function CompanyView({authFetch}: {authFetch: AuthenticatedFetch}) {
  const classes = useStyles();
  const { path, url } = useRouteMatch();
  return (
    <div className={classes.root}>
      <div className={classes.header}>
        <Typography variant="h2" color="primary">Entreprise</Typography>
      </div>
      <div className={classes.body}>
        <Tabs
          orientation="vertical"
          aria-label="Entreprise"
          className={classes.tabs}
          value={false}
        >
          <NavTab to={``} label="Accueil" />
          <NavTab to={`/forms`} label="Formulaires" />
          <NavTab to={`/sessions`} label="Sessions" />
          <NavTab to={`/bills`} label="Factures" />
        </Tabs>
        <div className={classes.panel}>
          <Switch>
            <Route path={`${path}/forms`}>
              Formulaires
            </Route>
            <Route path={`${path}/sessions`}>
              Sessions
            </Route>
            <Route path={`${path}/bills`}>
              Factures
            </Route>
            <Route path={path}>
              Welcome to the company page
            </Route>
          </Switch>
        </div>
      </div>
    </div>
  );
}
