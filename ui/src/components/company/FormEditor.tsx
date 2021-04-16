import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Fab, makeStyles, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField } from "@material-ui/core";
import React, { useEffect } from "react";
import { Form } from "../../api/company/form";
import * as Yup from "yup";
import { useFormik } from "formik";
import {Edit, Delete}  from '@material-ui/icons';
import AddIcon from '@material-ui/icons/Add';
import { Link, Route, Switch, useHistory, useParams, useRouteMatch } from "react-router-dom";

const useStylesCRUD = makeStyles({
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

type EditorData = {
  id?: string,
  name?: string,
  description?: string,
  tree?: Form.Tree,
};

type APIProps = {api: ReturnType<typeof Form.createApi>}

export const FormCRUD = ({api}: APIProps) => {
  const { path, url } = useRouteMatch();
  const history = useHistory();

  const backHome = () => history.push(url);

  return <div>
    <Switch>
      <Route path={`${path}/:id`} children={<FormEdit api={api} back={backHome} />} />
      <Route path={`${path}/new`}>
        <ValidatedForm
          initialValues={{}}
          submit={(data: Form.Create) => { api.create(data).catch(() => {}); }}
          back={backHome}
        />
      </Route>
      <Route path={path}>
        <FormOverview api={api}/>
      </Route>
    </Switch>
    
  </div>;
};

  const FormOverview = ({api}: APIProps) => {
    const classes = useStylesCRUD();
    const { path, url } = useRouteMatch();
    
    const [list, setList] = React.useState<Form.Full[]>([]);
  
    const listEntries = () => api.list().then(setList);
    const deleteEntry = (formId: string) =>
      api.delete(formId).then(listEntries).catch(() => {});

    useEffect(listEntries as any, []);

    return <>
      <TableContainer className={classes.table}>
        <Table aria-label="simple table">
          <TableHead>
            <TableRow>
              <TableCell>#</TableCell>
              <TableCell>Nom</TableCell>
              <TableCell>Description</TableCell>
              <TableCell align="right"></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {list.map(form => (
              <TableRow key={form.id}>
                <TableCell component="th" scope="row">
                  {form.id}
                </TableCell>
                <TableCell>{form.name}</TableCell>
                <TableCell>{form.description}</TableCell>
                <TableCell align="right">
                  <Link to={`${url}/${form.id}`} ><Edit className={classes.editIcon}/></Link>
                  <Delete className={classes.deleteIcon} onClick={() => deleteEntry(form.id)}/>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <Link to={`${url}/new`} >
        <Fab className={classes.addIcon} color="primary" aria-label="add">
          <AddIcon/>
        </Fab>
      </Link>
    </>;
  };

  const FormEdit = ({api, back}: APIProps & {back: () => void}) => {
    const { id } = useParams<{id: any}>();
    const [data, setData] = React.useState<Form.Full | undefined>(undefined);
    
    useEffect(() => api.getById(id).then(setData) as any, []);

    return data
      ? <ValidatedForm
          initialValues={data}
          submit={(data: Form.Update) => { api.update(data).catch(() => {}); }}
          back={back}
        />
      : <div>Loading</div>
  }
    

  const ValidatedForm = ({initialValues, back, submit}: {initialValues: EditorData, back: () => void, submit: ((_: Form.Create) => void) | ((_: Form.Update) => void) }) => {
    const { url } = useRouteMatch();
    const formik = useFormik<EditorData>({
      initialValues: {
        name: "",
        description: "",
        ...initialValues
      },
      validationSchema: Yup.object({
        name: Yup.string()
          .max(50, 'Too Long!')
          .required('Required')
      }),
      onSubmit:(values) => {
        submit(values as any);
        back();
      }
    });

    const editOrAddLabel = initialValues?.id ? "Editer" : "Ajouter";

    return <>
      <h2>{editOrAddLabel} un formulaire</h2>
      <TextField
        id="id"
        label="Id"
        fullWidth
        margin="dense"
        variant="outlined"
        defaultValue={initialValues.id}
        disabled={true}
      />
      <TextField
        id="name"
        label="Nom"
        fullWidth
        margin="normal"
        variant="outlined"
        value={formik.values.name}
        onChange={formik.handleChange}
        error={formik.touched.name && Boolean(formik.errors.name)}
        helperText={formik.touched.name && formik.errors.name}
      />
      <TextField
        id="description"
        label="Description"
        fullWidth
        margin="normal"
        variant="outlined"
        value={formik.values.description}
        onChange={formik.handleChange}
        error={formik.touched.description && Boolean(formik.errors.description)}
        helperText={formik.touched.description && formik.errors.description}
      />
    
      <Button color="primary" onClick={back}>
        Annuler
      </Button>
      <Button onClick={() => { formik.handleSubmit(); }} color="primary">
        {editOrAddLabel}
      </Button>
    </>
  }