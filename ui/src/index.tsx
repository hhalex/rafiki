import { Grid, makeStyles, Paper } from '@material-ui/core';
import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';


const useStyles = makeStyles((theme) => ({
  root: {
    display: 'flex',
    flexWrap: 'wrap',
    '& > *': {
      fontSize: "24px",
      padding: theme.spacing(2),
      margin: theme.spacing(3),
      width: theme.spacing(32),
      height: theme.spacing(5),
    },
  },
}));

const SimplePaper = ({children}: any) => {
  const classes = useStyles();

  return (
    <div className={classes.root}>
      <Paper>{children}</Paper>
    </div>
  );
}


ReactDOM.render(
  <React.StrictMode>
    <Grid
      container
      direction="row"
      justify="center"
      alignItems="center"
    >
      <SimplePaper>
        <Grid
          container
          direction="row"
          justify="center"
          alignItems="center"
        >Welcome on Rafiki</Grid>
      </SimplePaper>
    </Grid>
  </React.StrictMode>,
  document.getElementById('root')
);
