import React, { useEffect } from 'react';
import { makeStyles, Theme } from '@material-ui/core/styles';
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';
import { Link, Route, Switch, useLocation, useRouteMatch } from "react-router-dom";
import { Typography } from '@material-ui/core';
import { FormCRUD } from '../components/company/FormEditor';
import { Form } from '../api/company/form';
import { FormSessionCRUD } from '../components/company/FormSessionEditor';
import { FormSession } from '../api/company/session';
import { FormSessionInvite } from '../api/company/invite';
import { AuthAxios } from '../auth';
import { EmployeeInvite } from '../api/employee/inviteWithAnswer';
import { InviteAnswer } from '../api/employee/answer';
import { useState } from 'react';

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

export default function EmployeeView({authFetch}: {authFetch: AuthAxios}) {
  const classes = useStyles();
  const { path, url } = useRouteMatch();

  const employeeInvite = EmployeeInvite.createApi(authFetch);
  const inviteAnswer = InviteAnswer.createApi(authFetch);

  const [list, setList] = useState<EmployeeInvite.Full[]>([]);

  useEffect(() => {
    employeeInvite.getAll().run(setList);
  }, []);

  return (
    <div className={classes.root}>
      <div className={classes.header}>
        <Typography variant="h2" color="primary">Employé</Typography>
      </div>
      <div className={classes.body}>
        <Tabs
          orientation="vertical"
          aria-label="Employé"
          className={classes.tabs}
          value={false}
        >
          <NavTab to={``} label="Accueil" />
          <NavTab to={`/invites`} label="Invitations" />
        </Tabs>
        <div className={classes.panel}>
          <Switch>
            <Route path={`${path}/invites`}>
              <ul>{list.map((invite, i) => <li key={i}>{JSON.stringify(invite)}</li>)}</ul>
            </Route>
            <Route path={path}>
              Welcome to the employee page
            </Route>
          </Switch>
        </div>
      </div>
    </div>
  );
}
