// this is the JavaScript “library” file for all (P)Anki cards

// reveal(), reveal(true):
// .anki_reveal, #anki_answer appear via removal of CSS property “display: none”
// .anki_unreveal, (possibly #anki_question) disappear via CSS rule insertion: “display: none”
// reveal(false): does the opposite
// use for cloze deletion; “display: none” does not reserve space
// default onflip() calls reveal()

// visibility: hidden/visible;

// showHint(), showHint(true):
// .anki_hint appears via style override: “visibility: visible”
// showHint(false): does the reverse
// showHint(true/false, 'class_name') takes an optional class name to restrict it’s range to elements with
// these class names (in addition to .anki_hint)
// “visibility: invisible” reserves render space
// (in the future, there may be the possibility to use the “display” property as well: .anki_hint_block may turn
// into “display: block” on showHint()) or showHint(true, 'my_class')

// customize onflip() if you need a call to showHint() as well


anki = {
	SELECTOR_REVEAL: {
        REVEAL: '.anki_reveal, #anki_answer',
        UNREVEAL_RULE: '.anki_unreveal, #anki_identifier_blablarandom' +
            (anki_config.questionInAnswer ? ', #question' : ''),
        UNREVEAL_SUBSTRING: '#anki_identifier_blablarandom'
    },
    searchRule: function(rules, selector, test) {
		var stdTest = function(a, b) {
			return a === b;
		};
		var testFunction = test || stdTest;
		var i, r;
		for (i = 0; i < rules.cssRules.length; i++) {
			r = rules.cssRules[i];
			if (r.type === r.STYLE_TYPE && testFunction(r.selectorText, selector)) {
				return {rule: r, index: i};
			}
		}
	},
    reveal: function(reveal) {
		var styleSheet;
		var rule;
		var ruleReverse;
    	styleSheet = document.getElementById('anki_style').sheet;
    	// rule is there, but ruleReverse may not
    	rule = anki.searchRule(styleSheet, anki.SELECTOR_REVEAL.REVEAL);
		ruleReverse = anki.searchRule(styleSheet, anki.SELECTOR_REVEAL.UNREVEAL_SUBSTRING,
				function(text, selector) {return text.indexOf(selector) !== -1});
    	if (reveal !== false) {
    		// reveal answer; hide some other
    		rule.rule.style.removeProperty('display');
    		if (!ruleReverse) {
    			styleSheet.insertRule(anki.SELECTOR_REVEAL.UNREVEAL_RULE + ' { display: none; }', 0);
    		}
    	} else {
    		// hide answer (again); normalize some other
    		rule.rule.style.display = 'none';
    		if (ruleReverse) {
    			styleSheet.deleteRule(ruleReverse.index);
    		}
    	}
    },
    /**
     * Shows or hides the “hint” elements on a page.
     *
     * All elements which are in the classes “anki_hint” and @param className, if supplied, are selected.
     * Then the visibility CSS property is set to visible if @param show is true or absent. Otherwise it is
     * set to hidden.
     */
    showHint: function(show, className) {
        var i;
        var visibility = (show !== false) ? 'visible' : 'hidden';
        var nodes = document.getElementsByClassName('anki_hint ' + (className ? className : ''));
        for (i = 0; i < nodes.length; i++) {
                nodes[i].style.visibility = visibility;
        }
    },
    collectAudio: function() {
        // collect an register for (question, answer)
        var nodes;
        var i;
        var j;
        var qa = [{css: 'anki_question', java: anki_sound.QUESTION},
                  {css: 'anki_answer', java: anki_sound.ANSWER}];
        for (i = 0; i < 2; i++) {
            nodes = document.getElementById(qa[i].css);
            nodes = nodes.getElementsByTagName('audio');
            for (j = 0; j < nodes.length; j++) {
                anki_sound.register(nodes[j].src, qa[i].java);
            }
        }
    }
};

onflip = anki.reveal;
onload = anki.collectAudio;
