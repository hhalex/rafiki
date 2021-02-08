import React from 'react';
import { makeStyles, Theme } from '@material-ui/core/styles';
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';
import Box from '@material-ui/core/Box';

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
    minWidth:  640,

    margin: "auto",
   
  },
  header: {
    marginTop: "10vh",
    height: "10vh",
    textAlign: "center"
  },
  body: {
    height: "60vh",
    display: 'flex',
    flexGrow: 1,

  },
  tabs: {
    borderRight: `1px solid ${theme.palette.divider}`,
  },
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
            <h2>Administration</h2>
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
            <TabPanel value={value} index={0}>
                Contrats
                <hr style={{width:"100%"}}/>
            </TabPanel>
            <TabPanel value={value} index={1}>
                Entreprises
                <hr style={{width:"100%"}}/>
            </TabPanel>
            <TabPanel value={value} index={2}>
                Employés
                <hr style={{width:"250px"}}/>
            </TabPanel>
            <TabPanel value={value} index={3}>
                Factures
                <hr style={{width:"250px"}}/>
            </TabPanel>
        </div>
    </div>
  );
}