"""
Combines touch data with score data to determine right-of-way.
"""
import pandas as pd
import os

def main(prefix):
    fdir = prefix
    prefix = os.path.basename(fdir)
    
    # Load the CSV files
    frame_data = pd.read_csv(fdir + "/" + prefix + '_frame_data.csv')
    score_data = pd.read_csv(fdir + "/" + prefix + '_score_data.csv')
    
    # Create a new DataFrame for the output
    output_data = pd.DataFrame(columns=['start_frame', 
                                        'touch_frame', 
                                        'time',
                                        'prev_left', 
                                        'prev_right', 
                                        'next_left', 
                                        'next_right', 
                                        'two_left', 
                                        'two_right', 
                                        'right_of_way'])
    
    for index, row in frame_data.iterrows():
        print(f"Processing index {index}")
        touch_frame = row['touch_frame']
        if not row['refereed']:
            continue
        
        #left_white = row['left_white']
        #right_white = row['right_white']
        red = row['red']
        green = row['green']
        
        # Find previous, next, and second next frames
        prev_frame = score_data[score_data['frame_number'] < touch_frame].iloc[-1]
        next_frames = score_data[score_data['frame_number'] > touch_frame].iloc[:]
    
        next_score_frame_num = next_frames.iloc[0]['frame_number']
        prev_left = prev_frame['left_score']
        prev_right = prev_frame['right_score']
        next_left = next_frames.iloc[0]['left_score']
        next_right = next_frames.iloc[0]['right_score']
        two_left = next_frames.iloc[1]['left_score']
        two_right = next_frames.iloc[1]['right_score']
    
        if index + 1 < len(frame_data):
            # If there are more touches.
            next_row = frame_data.iloc[index+1]
            next_touch_frame = next_row['touch_frame']
            #next_left_white = row['left_white']
            #next_right_white = row['right_white']
            next_red = row['red']
            next_green = row['green']
        else:
            next_touch_frame = next_score_frame_num
    
        # If there was a video review, then somebody's score may go
        # down and then the two frame is the result.
        # This approach fails if simultaneous was the first call.
        if (two_left < next_left or two_right < next_right) and two_left != 0 and two_right != 0:
            correct_left = two_left
            correct_right = two_right
        else:
            correct_left = next_left
            correct_right = next_right
        
        do_full_check = True
        if next_score_frame_num > next_touch_frame:
            if next_red or next_green:
                if red and green:
                    right_of_way = 0
                else:
                    if red:
                        right_of_way = 1
                    else:
                        assert green
                        right_of_way = -1
                do_full_check = False
                print("Found another set of lights between first set and score update.", (touch_frame//25)//60,(touch_frame%60))
    
        if do_full_check:
            assert red or green
            if red:
                if correct_left > prev_left:
                    right_of_way = -1
                else:
                    right_of_way = 1
            else:
                if correct_right > prev_right:
                    right_of_way = 1
                else:
                    right_of_way = -1
        
        # Fill in the data
        output_data = output_data._append({
            'start_frame': row['start_frame'],
            'touch_frame': touch_frame,
            'time': row['time'],
            'prev_left': prev_left,
            'prev_right': prev_right,
            'next_left': next_left,
            'next_right': next_right,
            'two_left': two_left,
            'two_right': two_right,
            'right_of_way': right_of_way
        }, ignore_index=True)
    
    # Save the result to a new CSV file
    output_data.to_csv(fdir + "/" + prefix + '_touch_score.csv', index=False)
    
    print(f"Processing complete. Results saved to {prefix}_touch_score.csv")

if __name__ == "__main__":
    prefix = "C:\\Users\\drtod\\Documents\\fencing\\Shanghai_Red"
    main(prefix)