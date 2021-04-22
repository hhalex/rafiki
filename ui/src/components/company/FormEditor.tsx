import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Fab, List, ListItem, ListItemText, makeStyles, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField } from "@material-ui/core";
import React, { useEffect } from "react";
import { Form } from "../../api/company/form";
import * as Yup from "yup";
import { Formik, Form as FormikForm, Field as FormikField, getIn, FieldArray } from "formik";
import { Clear } from '@material-ui/icons';
import AddIcon from '@material-ui/icons/Add';
import { Link, Route, Switch, useHistory, useParams, useRouteMatch } from "react-router-dom";

const useStylesCRUD = makeStyles({
  table: {},
  formEntry: { 
    justifyContent: "space-between",
    "& .formEntryEdit": {
      cursor: "pointer",
      "&:hover": {
        textDecoration: "underline"
      }
    },
    "& > .deleteIcon": {
      visibility: "hidden"
    },
    "&:hover > .deleteIcon": {
      visibility: "visible",
      cursor: "pointer"
    }
  },
  addIcon: {
    margin: "2em",
    float: "right"
  },
  deleteIcon: {
    cursor: "pointer",
  }
});

type EditorData = {
  id?: string,
  name?: string,
  description?: string,
  tree?: Form.Tree.QuestionGroup,
};

type APIProps = { api: ReturnType<typeof Form.createApi> }

export const FormCRUD = ({ api }: APIProps) => {
  const { path, url } = useRouteMatch();
  const history = useHistory();

  const backHome = () => history.push(url);

  return <div style={{ margin: "1em" }}>
    <Switch>
      <Route path={`${path}/new`}>
        <ValidatedForm
          initialValues={{}}
          submit={(data: Form.Create) => { api.create(data).catch(() => { }); }}
          back={backHome}
        />
      </Route>
      <Route path={`${path}/:id`} children={<FormEdit api={api} back={backHome} />} />
      <Route path={path}>
        <FormOverview api={api} />
      </Route>
    </Switch>

  </div>;
};

const FormOverview = ({ api }: APIProps) => {
  const classes = useStylesCRUD();
  const { path, url } = useRouteMatch();

  const [list, setList] = React.useState<Form.Full[]>([]);

  const listEntries = () => api.list().then(setList);
  const deleteEntry = (formId: string) =>
    api.delete(formId).then(listEntries).catch(() => { });

  useEffect(listEntries as any, []);

  return <>
    <List className={classes.table}>
      {list.flatMap(form => ([
        <Divider key={`divider-${form.id}`}/>,
        <ListItem key={form.id} className={classes.formEntry} >
          <ListItemText  primary={<Link className="formEntryEdit" to={`${url}/${form.id}`} >{form.name}</Link>} secondary={form.description}/>
          <Clear className="deleteIcon" onClick={() => deleteEntry(form.id)} />
        </ListItem>]
      )).slice(1)}
    </List>
    <Link to={`${url}/new`} >
      <Fab className={classes.addIcon} color="primary" aria-label="add">
        <AddIcon />
      </Fab>
    </Link>
  </>;
};

const FormEdit = ({ api, back }: APIProps & { back: () => void }) => {
  const { id } = useParams<{ id: any }>();
  const [data, setData] = React.useState<Form.Full | undefined>(undefined);

  useEffect(() => api.getById(id).then(setData) as any, []);

  return data
    ? <ValidatedForm
      initialValues={data as EditorData}
      submit={(data: Form.Update) => { api.update(data).catch(() => { }); }}
      back={back}
    />
    : <div>Loading</div>
}


const useStylesVF = makeStyles({
  table: {},
  formEntry: { 
    justifyContent: "space-between",
    "& .formEntryEdit": {
      cursor: "pointer",
      "&:hover": {
        textDecoration: "underline"
      }
    },
    "& > .deleteIcon": {
      visibility: "hidden"
    },
    "&:hover > .deleteIcon": {
      visibility: "visible",
      cursor: "pointer"
    }
  },
  addQuestion: {
    display: "flex",
    margin: "1em 0",
    "& > button": {
      margin: "auto"
    }
  },
  deleteIcon: {
    cursor: "pointer",
  },
  validateGroup: {
    textAlign: "right"
  },
  questionLabel: {
    width: "70px",
    "& input": {
      fontFamily: "monospace"
    }
  },
  questionItem: {
    alignItems: "flex-start",
    "& .qlabel": {
      width: "50px",
      margin: "0 1em",
      "& input": {
        fontFamily: "monospace"
      }
    },
    "& .qtext": {
      flexGrow: 6
    },
    "& .deleteIcon": {
      margin: "1em",
      cursor: "pointer"
    }
  }
});

const validationSchema = Yup.object({
  name: Yup.string()
    .max(50, 'Too Long!')
    .required('Required'),
  questions: Yup
    .array()
    .of(Yup.object({
      label: Yup.string().required('Required'),
      text: Yup.string().required('Required')
    }))
    .test("Unique", "Labels need te be unique", function(values) {
      const labelCounts: {[key: string]: number[]} = {};

      if (values) {
        for(let i=0; i < values.length; i++) {
          const { label } = values[i];

          if (label && (label in labelCounts))
            labelCounts[label].push(i);
          else if (label)
            labelCounts[label] = [i];
        }

        return new Yup.ValidationError(
          Object.values(labelCounts).flatMap(ids => {
            return ids.length > 1
              ? ids.map(id => this.createError({
                path: `questions[${id}].label`,
                message: `Duplicate`
              }))
              : []
          })
        )
      }

      return false;
  })
});

const ValidatedForm = ({ initialValues, back, submit }: { initialValues: EditorData, back: () => void, submit: ((_: Form.Create) => void) | ((_: Form.Update) => void) }) => {
  const classes = useStylesVF();
  const editOrAddLabel = initialValues?.id ? "Editer" : "Ajouter";

  return <Formik
    initialValues={{
      id: initialValues.id,
      name: initialValues.name || "",
      description: initialValues.description || "",
      questions: initialValues.tree?.children || []
    }}
    validationSchema={validationSchema}
    onSubmit={(values) => {
      console.log(JSON.stringify({...values, tree: {children: values.questions}}));
      submit({...values, questions: undefined, tree: {children: values.questions}} as any);
      back();
    }}
  >{({ values, touched, errors, handleChange, handleBlur, isValid }) => (
    <FormikForm noValidate autoComplete="off">
      <h2>{editOrAddLabel} un formulaire</h2>
      <TextField
        name="name"
        label="Nom"
        margin="normal"
        value={values.name}
        onChange={handleChange}
        error={touched.name && Boolean(errors.name)}
        helperText={touched.name && errors.name}
      />
      <TextField
        name="description"
        label="Description"
        fullWidth
        multiline
        margin="normal"
        value={values.description}
        onChange={handleChange}
        error={touched.description && !!errors.description}
        helperText={touched.description && errors.description}
      />

      <h3>Structure</h3>
      <FieldArray name="questions">
        {({push, remove}) => (
          <List className="">{
            values.questions.map((question, i) => {

              const label = `questions[${i}].label`;
              const touchedLabel = getIn(touched, label);
              const errorLabel = getIn(errors, label);

              const text = `questions[${i}].text`;
              const touchedText = getIn(touched, text);
              const errorText = getIn(errors, text);

              return <ListItem key={"question" + i} className={classes.questionItem} >
                <TextField
                  name={label}
                  label="Label"
                  className="qlabel"
                  value={question.label}
                  onChange={handleChange}
                  error={touchedLabel && !!errorLabel}
                  helperText={touchedLabel && errorLabel}
                />
                <TextField
                  name={text}
                  label="Question"
                  className="qtext"
                  multiline
                  value={question.text}
                  onChange={handleChange}
                  error={touchedText && !!errorText}
                  helperText={touchedText && errorText}
                />
                <Clear className="deleteIcon" onClick={() => remove(i)}/>
              </ListItem>
            })
          }
            <ListItem className={classes.addQuestion}>
              <Fab
                color="primary"
                aria-label="add"
                onClick={() => push({label: `q-${values.questions.length.toLocaleString("fr-FR", { minimumIntegerDigits: 3 })}`, text: ""})}
              >
                <AddIcon />
              </Fab>
            </ListItem>
          </List>
        )}
      </FieldArray>

      <div className={classes.validateGroup}>
        <Button color="primary" onClick={back}>
          Annuler
        </Button>
        <Button type="submit" color="primary">
          {editOrAddLabel}
        </Button>
      </div>
    </FormikForm>
  )}
  </Formik>
}