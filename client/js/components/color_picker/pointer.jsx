'use strict';

import React from 'react';
import ReactCSS from 'reactcss';

export class ChromePointer extends ReactCSS.Component {

    classes() {
        return {
            'default': {
                picker: {
                    width: '12px',
                    height: '12px',
                    borderRadius: '6px',
                    transform: 'translate(-6px, -1px)',
                    backgroundColor: 'rgb(248, 248, 248)',
                    boxShadow: '0 1px 4px 0 rgba(0, 0, 0, 0.37)'
                }
            }
        };
    }

    render() {
        return(
            <div is="picker"></div>
        );
    }
}

export default ChromePointer;
