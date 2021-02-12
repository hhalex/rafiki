import React, { useEffect } from 'react';
import { makeStyles, Theme } from '@material-ui/core/styles';
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';
import Box from '@material-ui/core/Box';
import AddIcon from '@material-ui/icons/Add';
import { TableContainer, Table, TableHead, TableRow, TableCell, TableBody, Fab, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField, Typography } from '@material-ui/core';

import EditIcon from '@material-ui/icons/Edit';
import DeleteIcon from '@material-ui/icons/Delete';
import { useRecoilValue } from 'recoil';
import { bearerToken } from '../atoms/auth';


interface TabPanelProps {
  children?: React.ReactNode;
  index: any;
  value: any;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`vertical-tabpanel-${index}`}
      aria-labelledby={`vertical-tab-${index}`}
      {...other}
    >
      {value === index && (
        <Box p={3}>
          {children}
        </Box>
      )}
    </div>
  );
}

function a11yProps(index: any) {
  return {
    id: `vertical-tab-${index}`,
    'aria-controls': `vertical-tabpanel-${index}`,
  };
}

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

export default function VerticalTabs() {
  const classes = useStyles();
  const [value, setValue] = React.useState(0);

  const handleChange = (event: React.ChangeEvent<{}>, newValue: number) => {
    setValue(newValue);
  };

  return (
    <div className={classes.root}>
      <div className={classes.header}>
        <Typography variant="h2" color="primary">Administration</Typography>
      </div>
      <div className={classes.body}>
        <Tabs
          orientation="vertical"
          value={value}
          onChange={handleChange}
          aria-label="Administration"
          className={classes.tabs}
        >
          <Tab label="Contrats" {...a11yProps(0)} />
          <Tab label="Entreprises" {...a11yProps(1)} />
          <Tab label="Employés" {...a11yProps(2)} />
          <Tab label="Factures" {...a11yProps(3)} />
        </Tabs>
        <div className={classes.panel}>
          <TabPanel value={value} index={0}>
            Contrats
                  <hr style={{ width: "100%" }} />
          </TabPanel>
          <TabPanel value={value} index={1}>
            <CompanyCRUD />
          </TabPanel>
          <TabPanel value={value} index={2}>
            Employés
                  <hr style={{ width: "250px" }} />
          </TabPanel>
          <TabPanel value={value} index={3}>
            Factures
                  <hr style={{ width: "250px" }} />
          </TabPanel>
        </div>
      </div>
    </div>
  );
}

function createData(id: string, name: string, email: string) {
  return { name, email, id };
}

const rows = [
  createData("10cc8266-d161-40ce-849f-9d0b6406b627",'Apple Inc.', "pdg@apple.com"),
  createData("1e1e91e5-7e96-4665-8009-997f8f4aa0a7", 'Tesla', "pdg@tesla.com"),
  createData("f8bbec7b-a730-49d6-9ebf-c8b8ee17ac1b", 'Dogecoin', "pdg@doge.com"),
];

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

const CompanyCRUD = () => {
  const classes = useStylesCRUD();

  const bt = useRecoilValue(bearerToken) as string;

  type CompanyFormData = {
    id?: string,
    name?: string,
    email?: string,
    password?: string,
  };

  type CompanyANDUser = [{
    id: string,
    name: string,
    rh_user: string
  },
  {
    id: string,
    username: string
  }];

  const [companiesList, setCompaniesList] = React.useState<CompanyANDUser[]>([]);

  const [companyFormData, setCompanyFormData] = React.useState<CompanyFormData | undefined>(undefined);

  const handleOpenAddCompanyForm = () => {
    setCompanyFormData({});
  }

  const createHandleOpenEditCompanyForm = (id: string, name: string, email: string) => () => {
    setCompanyFormData({id, name, email});
  }

  const handleClose = () => {
    setCompanyFormData(undefined);
  };

  const loadCompanies = () => fetch("/company", {headers: [["Authorization", bt]]})
    .then(b => b.json().then(setCompaniesList))
    .catch(() => {});

  const editOrAddCompany = (c: CompanyFormData) => {
    const add = !c.id;
    const data = {
      id: c.id,
      rh_user_email: c.email,
      name: c.name,
      rh_user_password: c.password ? c.password : undefined
    };
    return fetch("/company", {
      headers: [["Authorization", bt]],
      method: add ? "POST" : "PUT",
      body: JSON.stringify(data)
    }).then(loadCompanies)
    .catch(() => {});
  }

  const createHandleDeleteCompany = (companyId: string) => () => {
    return fetch("/company", {
      headers: [["Authorization", bt]],
      method: "DELETE",
      body: companyId
    }).then(loadCompanies)
    .catch(() => {});
  }

  useEffect(loadCompanies as any, []);

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
          {companiesList.map(([company, user]) => (
            <TableRow key={company.id}>
              <TableCell component="th" scope="row">
                {company.id}
              </TableCell>
              <TableCell>{company.name}</TableCell>
              <TableCell>{user.username}</TableCell>
              <TableCell align="right">
                <EditIcon className={classes.editIcon} onClick={createHandleOpenEditCompanyForm(company.id, company.name, "email")}/>
                <DeleteIcon className={classes.deleteIcon} onClick={createHandleDeleteCompany(company.id)}/>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
    <Fab className={classes.addIcon} color="primary" aria-label="add" onClick={handleOpenAddCompanyForm} >
      <AddIcon/>
    </Fab>
    {companyFormData && <Dialog open={!!companyFormData} onClose={handleClose} aria-labelledby="form-dialog-title">
      <DialogTitle id="form-dialog-title">{companyFormData.id ? "Editer" : "Ajouter"} une entreprise</DialogTitle>
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
          defaultValue={companyFormData.email}
          onChange={e => {setCompanyFormData(old => ({...old, email: e.target.value}))}}

        />
        <TextField
          id="password"
          label="Mot de Passe"
          type="password"
          fullWidth
          margin="normal"
          variant="outlined"
          onChange={e => {setCompanyFormData(old => ({...old, password: e.target.value}))}}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} color="primary">
          Annuler
        </Button>
        <Button onClick={() => editOrAddCompany(companyFormData).then(handleClose)} color="primary">
          Ajouter
        </Button>
      </DialogActions>
    </Dialog>}
  </div>;
}
