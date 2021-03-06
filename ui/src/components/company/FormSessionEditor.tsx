import { Button, Divider, FormControl, IconButton, InputLabel, List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText, makeStyles, MenuItem, Select, TextField } from "@material-ui/core";
import React, { useEffect } from "react";
import { FormSession } from "../../api/company/session";
import * as Yup from "yup";
import { Formik, Form as FormikForm, Field as FormikField, getIn, FieldArray } from "formik";
import { ArrowRightAltOutlined, Clear } from '@material-ui/icons';
import AddIcon from '@material-ui/icons/Add';
import EditIcon from '@material-ui/icons/Edit';
import PlayArrowIcon from '@material-ui/icons/PlayArrow';
import StopIcon from '@material-ui/icons/Stop';
import DoneIcon from '@material-ui/icons/Done';
import { Link, Route, Switch, useHistory, useParams, useRouteMatch } from "react-router-dom";
import { Form } from "../../api/company/form";
import { FormSessionInvite } from "../../api/company/invite";
import { Tk } from "../../tk";

const useStylesCRUD = makeStyles({
  table: {},
});

type EditorData = {
  id?: string,
  name?: string,
  formId?: string
  invites: { user?: string, team?: string }[]
};

type APIProps = { apiSession: FormSession.Api, apiForm: Form.Api, apiInvite: FormSessionInvite.Api }

export const FormSessionCRUD = ({ apiSession, apiForm, apiInvite }: APIProps & { apiForm: Form.Api }) => {
  const { path, url } = useRouteMatch();
  const history = useHistory();

  const backHome = () => history.push(url);

  return <div style={{ margin: "1em" }}>
    <Switch>
      <Route path={`${path}/new`}>
        <FormEdit apiSession={apiSession} apiForm={apiForm} apiInvite={apiInvite} back={backHome} />
      </Route>
      <Route path={`${path}/:id`} children={<FormEdit apiSession={apiSession} apiForm={apiForm} apiInvite={apiInvite} back={backHome} />} />
      <Route path={path}>
        <FormOverview apiSession={apiSession} />
      </Route>
    </Switch>

  </div>;
};

const FormOverview = ({ apiSession }: { apiSession: FormSession.Api }) => {
  const classes = useStylesCRUD();
  const { path, url } = useRouteMatch();

  const [list, setList] = React.useState<FormSession.Full[]>([]);

  const listEntries = apiSession.list().map(setList);
  const deleteEntry = (formSessionId: string) =>
    apiSession.delete(formSessionId).andThen(listEntries);

  useEffect(listEntries.eval as any, []);

  const enum SessionState {
    PENDING = "PENDING",
    STARTED = "STARTED",
    FINISHED = "FINISHED"
  }

  const sessionState = ({ startDate, endDate }: FormSession.Full) =>
    (!startDate && !endDate)
      ? SessionState.PENDING
      : (startDate && !endDate)
        ? SessionState.STARTED
        : SessionState.FINISHED;

  return <List className={classes.table}>
    {list.flatMap(formSession => {
      const sState = sessionState(formSession);
      return [
      <Divider key={`divider-${formSession.id}`} />,
      <ListItem key={formSession.id}>
        <ListItemText primary={formSession.name} secondary={sState} />
        <ListItemSecondaryAction>
          {(sState == SessionState.PENDING)
            ? <IconButton onClick={apiSession.start(formSession.id).andThen(listEntries).eval}>
                <PlayArrowIcon />
              </IconButton>
            : (sState == SessionState.STARTED)
              ? <IconButton onClick={apiSession.finish(formSession.id).andThen(listEntries).eval}>
                  <StopIcon />
                </IconButton>
              : <IconButton disabled>
                  <DoneIcon />
                </IconButton>
          }
          <IconButton component={Link} to={`${url}/${formSession.id}`}>
            <EditIcon />
          </IconButton>
          <IconButton onClick={deleteEntry(formSession.id).eval}>
            <Clear />
          </IconButton>
        </ListItemSecondaryAction>
      </ListItem>]}).slice(1)}
    <ListItem key="new">
      <Button
        onClick={() => { }}
        variant="outlined"
        size="medium"
        color="primary"
        aria-label="add"
        startIcon={<AddIcon />}
        component={Link}
        to={`${url}/new`}
      >Session</Button>
    </ListItem>
  </List>;
};

type InvitesList = {id?: string, user: string, team: string}[]

const FormEdit = ({ apiSession, apiInvite, apiForm, back }: APIProps & { back: () => void }) => {
  const { id } = useParams<{ id: any }>();

  const [forms, setForms] = React.useState<Form.Full[]>([]);
  useEffect(apiForm.list().map(setForms).eval as any, []);

  if (id !== undefined) {
    const [data, setData] = React.useState<FormSession.Full & { invites: FormSessionInvite.Full[] } | undefined>(undefined);
    useEffect(() =>
      Tk.all(apiSession.getById(id), apiInvite.listByFormSession(id))
        .map(([session, invites]) => setData({ ...session, invites })).eval as any, []);

    return data
      ? <ValidatedForm
        forms={forms}
        initialValues={data as EditorData}
        submit={(data: FormSession.Update, invites: InvitesList, deletedInvites: InvitesList) => {
          const deletedPromises = Tk.all(...deletedInvites.map(inv => apiInvite.delete(inv.id!)));
          const invitePromises = Tk.all(...invites.flatMap(inv => inv.id ? apiInvite.update(inv as FormSessionInvite.Update) : apiInvite.create(inv, data.id)));
          return Tk.all(apiSession.update(data), invitePromises, deletedPromises);
        }}
        back={back}
      />
      : <div>Loading</div>
  }

  return <ValidatedForm
    forms={forms}
    initialValues={{ invites: [] }}
    submit={(data: FormSession.Create, invites: InvitesList) =>
      apiSession.create(data, data.formId)
        .flatMap(formSession => Tk.all(...invites.map(inv => apiInvite.create(inv, formSession.id))))
    }
    back={back}
  />
}


const useStylesVF = makeStyles(theme => ({
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
  questionListItem: {
    "& .qlabel": {
      flexGrow: 10,
      marginRight: "1em",
      "& input": {
        fontFamily: "monospace"
      }
    },
    "& .qtext": {
      flexGrow: 10
    }
  },
  questionItem: {
    display: "flex"
  },

  answersList: {
    marginLeft: "2em",
    borderLeft: "1px solid rgba(0, 0, 0, 0.12);",

  },
  answerListItem: {
    "& .avalue": {
      width: "40px",
      marginRight: "1em",
      "& input": {
        fontFamily: "monospace"
      }
    },
    "& .alabel": {
      flexGrow: 10
    }
  },
  answerItem: {
    display: "flex"
  },
  formControl: {
    margin: theme.spacing(2),
    width: "300px",
  }
}));

const validationSchema = Yup.object({
  name: Yup.string()
    .max(50, 'Too Long!')
    .required('Required'),
  formId: Yup.number().required("Required"),
  invites: Yup
    .array()
    .of(Yup.object({
      user: Yup.string().email().required('Required'),
      team: Yup.string().required('Required')
    }))
    .test("Unique", "Emails need te be unique", function (values) {
      const userCounts: { [key: string]: number[] } = {};

      if (values) {
        for (let i = 0; i < values.length; i++) {
          const { user } = values[i];

          if (user && (user in userCounts))
            userCounts[user].push(i);
          else if (user)
            userCounts[user] = [i];
        }

        return new Yup.ValidationError(
          Object.values(userCounts).flatMap(ids => {
            return ids.length > 1
              ? ids.map(id => this.createError({
                path: `invites[${id}].user`,
                message: `Duplicate`
              }))
              : []
          })
        )
      }

      return false;
    })
});

type SubmitF = ((_1: FormSession.Create, _2: InvitesList, deleted: InvitesList) => Tk<any>) | ((_1: FormSession.Update, _2: InvitesList, deleted: InvitesList) => Tk<any>);

const ValidatedForm = ({ initialValues, back, forms, submit }: { initialValues: EditorData, forms: Form.Full[], back: () => void, submit: SubmitF }) => {
  const classes = useStylesVF();
  const editOrAddLabel = initialValues?.id ? "Editer" : "Ajouter";

  return <Formik
    initialValues={{
      id: initialValues.id,
      name: initialValues.name || "",
      formId: initialValues.formId || "",
      invites: initialValues.invites || []
    }}
    validationSchema={validationSchema}
    validateOnChange={false}
    onSubmit={(values) => {
      // Compare current invites with initial ones, to find out which ones have been deleted
      const deletedInvites = initialValues.invites.filter(i => !values.invites.find(newInvites => newInvites.user === i.user))
      submit({
          ...values,
          invites: undefined
        } as any,
        values.invites as {user: string, team: string}[],
        deletedInvites as {user: string, team: string}[]
      )
      .run(back)
    }}
  >{({ values, touched, errors, handleChange }) => (
    <FormikForm noValidate autoComplete="off">
      <h2>{editOrAddLabel} une session de test</h2>
      <TextField
        name="name"
        label="Nom"
        margin="normal"
        value={values.name}
        onChange={handleChange}
        error={touched.name && Boolean(errors.name)}
        helperText={touched.name && errors.name}
      />

      <FormControl className={classes.formControl}>
        <InputLabel id="form-id">Formulaire</InputLabel>
        <Select
          labelId="form-id"
          name="formId"
          value={values.formId}
          onChange={handleChange}
        >{forms.map(f => (<MenuItem key={f.id} value={f.id}>{f.name}</MenuItem>))}</Select>
      </FormControl>

      <h3>Invitations</h3>
      <FieldArray name="invites">
        {inviteHelper => (
          <List>{
            values.invites.map((invite, inviteIndex) => {
              const user = `invites[${inviteIndex}].user`;
              const touchedUser = getIn(touched, user);
              const errorUser = getIn(errors, user);

              const team = `invites[${inviteIndex}].team`;
              const touchedTeam = getIn(touched, team);
              const errorTeam = getIn(errors, team);
              return <ListItem key={"invite" + inviteIndex} className={classes.questionListItem} alignItems="flex-start">
                <ListItemIcon>
                  <ArrowRightAltOutlined />
                </ListItemIcon>
                <ListItemText>
                  <div className={classes.questionItem}>
                    <TextField
                      name={user}
                      label="User e-mail"
                      className="qlabel"
                      value={invite.user}
                      onChange={handleChange}
                      error={touchedUser && !!errorUser}
                      helperText={touchedUser && errorUser}
                    />
                    <TextField
                      name={team}
                      label="Team"
                      className="qtext"
                      value={invite.team}
                      onChange={handleChange}
                      error={touchedTeam && !!errorTeam}
                      helperText={touchedTeam && errorTeam}
                    />
                  </div>
                </ListItemText>

                <IconButton onClick={() => inviteHelper.remove(inviteIndex)}>
                  <Clear />
                </IconButton>

              </ListItem>
            })
          }
            <ListItem className={classes.addQuestion}>
              <Button
                variant="outlined"
                size="medium"
                color="primary"
                aria-label="add"
                startIcon={<AddIcon />}
                onClick={() => inviteHelper.push({ user: `myemail@tagada.com`, team: "/" })}
              >Invite</Button>
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