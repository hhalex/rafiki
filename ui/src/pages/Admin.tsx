import React, { useEffect } from 'react';
import { makeStyles, Theme } from '@material-ui/core/styles';
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';
import AddIcon from '@material-ui/icons/Add';
import { TableContainer, Table, TableHead, TableRow, TableCell, TableBody, Fab, Button, Dialog, DialogActions, DialogContent, TextField, Typography, DialogTitle } from '@material-ui/core';
import { Link, NavLink, Route, Switch, useLocation, useRouteMatch } from "react-router-dom";
import EditIcon from '@material-ui/icons/Edit';
import DeleteIcon from '@material-ui/icons/Delete';
import { Company } from '../api/company';
import type { AuthAxios } from '../auth';

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


export default function AdminView({authFetch}: {authFetch: AuthAxios}) {
  const classes = useStyles();
  const { path, url } = useRouteMatch();
  return (
    <div className={classes.root}>
      <div className={classes.header}>
        <Typography variant="h2" color="primary">Administration</Typography>
      </div>
      <div className={classes.body}>
        <Tabs
          orientation="vertical"
          aria-label="Administration"
          className={classes.tabs}
          value={false}
        >
          <NavTab to={`/companies`} label="Entreprises" />
          <NavTab to={`/employees`} label="Employés" />
          <NavTab to={`/bills`} label="Factures" />
          <NavTab to={`/doctors`} label="Médecins" />
        </Tabs>
        <div className={classes.panel}>
          <Switch>
            <Route path={`${path}/companies`}>
              <CompanyCRUD authFetch={authFetch} />
            </Route>
            <Route path={`${path}/employees`}>
              Employés <hr style={{ width: "250px" }} />
            </Route>
            <Route path={`${path}/doctors`}>
              Médecins <hr style={{ width: "250px" }} />
            </Route>
            <Route path={`${path}/bills`}>
              Factures <hr style={{ width: "250px" }} />
            </Route>
            <Route path={path}>
              Welcome to the admin page
            </Route>
          </Switch>
        </div>
      </div>
    </div>
  );
}

const useStylesCRUD = makeStyles({
  root: {},
  table: {  },
  addIcon: {
    margin: "2em",
    float: "right"
  },
  editIcon: {
    cursor: "pointer",
  },
  deleteIcon: {
    cursor: "pointer",
  }
});

const CompanyCRUD = ({authFetch}: {authFetch: AuthAxios}) => {
  const classes = useStylesCRUD();

  const api = Company.createApi(authFetch);

  type CompanyFormData = {
    id?: string,
    name?: string,
    rh_user_email?: string,
    rh_user_password?: string,
  };

  const [companiesList, setCompaniesList] = React.useState<Company.Full[]>([]);
  const [companyFormData, setCompanyFormData] = React.useState<CompanyFormData | undefined>(undefined);

  const initAddCompanyForm = () => {
    setCompanyFormData({});
  }

  const initEditCompanyForm = (c: Company.Full) => {
    setCompanyFormData({
      id: c.id,
      name: c.name,
      rh_user_email: c.rh_user.username,
      rh_user_password: ""
    });
  }

  const handleClose = () => {
    setCompanyFormData(undefined);
  };

  const loadCompanies = () => api.list().then(setCompaniesList);

  const editOrAddCompany = (c: CompanyFormData) => {
    const emptyPromise = new Promise<void>(_ => {});
    if (c.name && c.rh_user_email && c.rh_user_password !== undefined) {

      const data = {
        name: c.name,
        rh_user: {
          username: c.rh_user_email,
          password: c.rh_user_password
        }
      };

      // No id means we create a new entry
      if (!c.id) {
        // Password has to be a non empty string for user creation
        return c.rh_user_password.length > 0
          ? api.create(data)
            .then(loadCompanies)
            .catch(() => {})
          : emptyPromise;
      }
      
      return api.update({...data, id: c.id})
        .then(loadCompanies)
        .catch(() => {});
    }

    return emptyPromise;
  };

  const deleteCompany = (companyId: string) =>
    api.delete(companyId).then(loadCompanies).catch(() => {});

  useEffect(loadCompanies as any, []);

  const editOrAddLabel = companyFormData?.id ? "Editer" : "Ajouter";

  return <div className={classes.root}>
    <TableContainer className={classes.table}>
      <Table aria-label="simple table">
        <TableHead>
          <TableRow>
            <TableCell>#</TableCell>
            <TableCell>Nom</TableCell>
            <TableCell>Email</TableCell>
            <TableCell align="right"></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {companiesList.map(company => (
            <TableRow key={company.id}>
              <TableCell component="th" scope="row">
                {company.id}
              </TableCell>
              <TableCell>{company.name}</TableCell>
              <TableCell>{company.rh_user.username}</TableCell>
              <TableCell align="right">
                <EditIcon className={classes.editIcon} onClick={() => initEditCompanyForm(company)}/>
                <DeleteIcon className={classes.deleteIcon} onClick={() => deleteCompany(company.id)}/>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
    <Fab className={classes.addIcon} color="primary" aria-label="add" onClick={initAddCompanyForm} >
      <AddIcon/>
    </Fab>
    {companyFormData && <Dialog open={!!companyFormData} onClose={handleClose} aria-labelledby="form-dialog-title">
      <DialogTitle id="form-dialog-title">{editOrAddLabel} une entreprise</DialogTitle>
      <DialogContent>
        <TextField
          id="id"
          label="Id"
          fullWidth
          margin="dense"
          variant="outlined"
          defaultValue={companyFormData.id}
          disabled={true}
        />
        <TextField
          id="name"
          label="Nom"
          fullWidth
          margin="normal"
          variant="outlined"
          defaultValue={companyFormData.name}
          onChange={e => {setCompanyFormData(old => ({...old, name: e.target.value}))}}
        />
        <TextField
          id="email"
          label="Adresse e-mail/Identifiant"
          type="email"
          fullWidth
          margin="normal"
          variant="outlined"
          defaultValue={companyFormData.rh_user_email}
          onChange={e => {setCompanyFormData(old => ({...old, rh_user_email: e.target.value}))}}

        />
        <TextField
          id="password"
          label="Mot de Passe"
          type="password"
          fullWidth
          margin="normal"
          variant="outlined"
          onChange={e => {setCompanyFormData(old => ({...old, rh_user_password: e.target.value}))}}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} color="primary">
          Annuler
        </Button>
        <Button onClick={() => editOrAddCompany(companyFormData).then(handleClose)} color="primary">
          {editOrAddLabel}
        </Button>
      </DialogActions>
    </Dialog>}
  </div>;
}
