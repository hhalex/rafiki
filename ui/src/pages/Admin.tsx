import React, { useEffect } from 'react';
import { makeStyles, Theme } from '@material-ui/core/styles';
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';
import Box from '@material-ui/core/Box';
import AddIcon from '@material-ui/icons/Add';
import { TableContainer, Table, TableHead, TableRow, TableCell, TableBody, Fab, Button, Dialog, DialogActions, DialogContent, TextField, Typography, DialogTitle } from '@material-ui/core';

import EditIcon from '@material-ui/icons/Edit';
import DeleteIcon from '@material-ui/icons/Delete';
import { AuthenticatedFetch } from '../atoms/Auth';
import { CreateCompany, createCompanyApi, FullCompany, UpdateCompany } from '../api/company';


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

export default function AdminView({authFetch}: {authFetch: AuthenticatedFetch}) {
  const classes = useStyles();
  const [value, setValue] = React.useState(0);

  const handleChange = (_: React.ChangeEvent<{}>, newValue: number) => {
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
            <CompanyCRUD authFetch={authFetch} />
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

const CompanyCRUD = ({authFetch}: {authFetch: AuthenticatedFetch}) => {
  const classes = useStylesCRUD();

  const api = createCompanyApi(authFetch);

  type CompanyFormData = {
    id?: string,
    name?: string,
    rh_user_email?: string,
    rh_user_password?: string,
  };

  const [companiesList, setCompaniesList] = React.useState<FullCompany[]>([]);
  const [companyFormData, setCompanyFormData] = React.useState<CompanyFormData | undefined>(undefined);

  const initAddCompanyForm = () => {
    setCompanyFormData({});
  }

  const initEditCompanyForm = (c: FullCompany) => {
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
