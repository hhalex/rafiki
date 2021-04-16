import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Fab, makeStyles, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField } from "@material-ui/core";
import React, { useEffect } from "react";
import { Form } from "../../api/company/form";
import * as Yup from "yup";
import { useFormik } from "formik";
import {Edit, Delete}  from '@material-ui/icons';
import AddIcon from '@material-ui/icons/Add';

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

type EditorData = {
  id?: string,
  name?: string,
  description?: string,
  tree?: Form.Tree,
};

export const FormCRUD = ({api}: {api: ReturnType<typeof Form.createApi>}) => {
    const classes = useStylesCRUD();
  
    const [list, setList] = React.useState<Form.Full[]>([]);
    const [editorData, setEditorData] = React.useState<EditorData | undefined>(undefined);
  
    const initAdd = () => setEditorData({});
    const initEdit = setEditorData;
    const handleClose = () => setEditorData(undefined);
  
    const listEntries = () => api.list().then(setList);
    
    const editOrAdd = (data: Form.Create | Form.Update) => {
        if ("id" in data) {
          return api.update(data)
            .then(listEntries)
            .catch(() => {});
            
        }
        return api.create(data)
                .then(listEntries)
                .catch(() => {})
    };
  
    const deleteEntry = (formId: string) =>
      api.delete(formId).then(listEntries).catch(() => {});
  
    useEffect(listEntries as any, []);
  
    return <div className={classes.root}>
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
                  <Edit className={classes.editIcon} onClick={() => initEdit(form)}/>
                  <Delete className={classes.deleteIcon} onClick={() => deleteEntry(form.id)}/>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <Fab className={classes.addIcon} color="primary" aria-label="add" onClick={initAdd} >
        <AddIcon/>
      </Fab>
      {editorData && <ValidatedForm initialValues={{...editorData}} close={handleClose} submit={editOrAdd}/>}
    </div>;
  };

  const ValidatedForm = ({initialValues, close, submit}: {initialValues: EditorData, close: () => void, submit: (e: Form.Create | Form.Update) => void}) => {
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
        close();
      }
    });

    const editOrAddLabel = initialValues?.id ? "Editer" : "Ajouter";

    return <Dialog open onClose={close} aria-labelledby="form-dialog-title">
      <DialogTitle id="form-dialog-title">{editOrAddLabel} un formulaire</DialogTitle>
        <DialogContent>
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
        </DialogContent>
        <DialogActions>
          <Button onClick={close} color="primary">
            Annuler
          </Button>
          <Button onClick={() => { formik.handleSubmit(); }} color="primary">
            {editOrAddLabel}
          </Button>
        </DialogActions>
      </Dialog>
  }
  