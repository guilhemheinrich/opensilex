import { ApiServiceBinder } from './lib'
import FactorView from './components/factors/FactorView.vue';
import FactorList from './components/factors/FactorList.vue';
// import FactorLevelView from './components/factors/FactorLevelView.vue';
// import FactorLevelList from './components/factors/FactorLevelList.vue';
// import FactorLevelList from './components/factorLevels/FactorLevelList.vue';

//import ExperimentList from './components/experiments/ExperimentList.vue';

export default {
    install(Vue, options) {
        ApiServiceBinder.with(Vue.$opensilex.getServiceContainer());
    },
    
    components: {
       "opensilex-core-FactorView": FactorView,
       "opensilex-core-FactorList": FactorList
    //    "opensilex-core-FactorLevelView": FactorLevelView,
    //    "opensilex-core-FactorLevelList": FactorLevelList
//        "opensilex-core-ExperimentList": ExperimentList
    },

    // lang: {
    //     "fr": require("./lang/message-fr.json"),
    //     "en-US": require("./lang/message-en-US.json"),
    // }

};